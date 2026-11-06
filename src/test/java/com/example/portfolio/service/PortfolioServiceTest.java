package com.example.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.example.portfolio.config.PortfolioProperties;
import com.example.portfolio.europepmc.ArticleRecord;
import com.example.portfolio.europepmc.ArticleRecord.FundingReference;
import com.example.portfolio.europepmc.ArticleSearchResult;
import com.example.portfolio.europepmc.ArticlesClient;
import com.example.portfolio.europepmc.GrantRecord;
import com.example.portfolio.europepmc.GrantSearchResult;
import com.example.portfolio.europepmc.GrantsClient;
import com.example.portfolio.europepmc.UpstreamException;
import com.example.portfolio.model.LookupStatus;
import com.example.portfolio.model.PortfolioResponse;
import com.example.portfolio.model.PortfolioResponse.GrantEntry;

/**
 * Exercises orchestration with in-memory fakes: no HTTP, no Spring context, deterministic.
 */
class PortfolioServiceTest {

    private static final int POOL_SIZE = 3;
    private final ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE);

    @AfterEach
    void shutdown() {
        pool.shutdownNow();
    }

    private PortfolioService service(List<GrantRecord> grants, Integer hitCount,
                                     Function<String, ArticleSearchResult> articles, Duration deadline) {
        GrantsClient grantsClient = q -> new GrantSearchResult(hitCount, grants);
        ArticlesClient articlesClient = (q, size) -> articles.apply(q);
        PortfolioProperties props = new PortfolioProperties("x", "y", Duration.ofSeconds(1), Duration.ofSeconds(1),
                POOL_SIZE, deadline);
        return new PortfolioService(grantsClient, articlesClient, pool, props);
    }

    private static GrantRecord grant(String id) {
        return new GrantRecord(id, "Title " + id, "Wellcome Trust", "University of Cambridge", null);
    }

    private static ArticleSearchResult confirmedFor(String grantId, String articleId) {
        return new ArticleSearchResult(1, List.of(new ArticleRecord("MED", articleId, articleId, null, null, "T",
                "J", "2021", List.of(new FundingReference(grantId, "Wellcome Trust")))));
    }

    private static String idFromQuery(String q) {
        return q.substring("GRANT_ID:\"".length(), q.length() - 1);
    }

    @Test
    void oneFailedLookupDoesNotAffectTheOthersAndIsDistinctFromNoneFound() {
        List<GrantRecord> grants = List.of(grant("A"), grant("B"), grant("C"));
        PortfolioService service = service(grants, 3, q -> switch (idFromQuery(q)) {
            case "A" -> confirmedFor("A", "1");
            case "B" -> throw new UpstreamException("Europe PMC Articles API", "Europe PMC Articles API timed out", true, null);
            default -> new ArticleSearchResult(0, List.of());
        }, Duration.ofSeconds(5));

        PortfolioResponse response = service.build("University of Cambridge", 10, 3);

        assertThat(response.grants()).extracting(g -> g.publicationLookup().status())
                .containsExactly(LookupStatus.FOUND, LookupStatus.LOOKUP_FAILED, LookupStatus.NONE_FOUND);
        assertThat(response.grants().get(1).publicationLookup().error()).contains("timed out");
        assertThat(response.grants().get(2).publicationLookup().error()).isNull();
        assertThat(response.summary().grantsByLookupStatus())
                .containsEntry(LookupStatus.FOUND, 1)
                .containsEntry(LookupStatus.LOOKUP_FAILED, 1)
                .containsEntry(LookupStatus.NONE_FOUND, 1);
    }

    @Test
    void resultsStayUnderTheRightGrantWhateverOrderLookupsFinishIn() {
        List<GrantRecord> grants = List.of(grant("A"), grant("B"), grant("C"));
        Map<String, Long> delays = Map.of("A", 300L, "B", 150L, "C", 0L); // A finishes last
        PortfolioService service = service(grants, 3, q -> {
            String id = idFromQuery(q);
            sleep(delays.get(id));
            return confirmedFor(id, "pub-" + id);
        }, Duration.ofSeconds(5));

        PortfolioResponse response = service.build("University of Cambridge", 10, 3);

        assertThat(response.grants()).extracting(GrantEntry::id).containsExactly("A", "B", "C");
        assertThat(response.grants()).allSatisfy(g ->
                assertThat(g.publications()).singleElement()
                        .satisfies(p -> assertThat(p.id()).isEqualTo("pub-" + g.id())));
    }

    @Test
    void lookupsOverlapButNeverExceedThePoolSize() {
        List<GrantRecord> grants = List.of(grant("1"), grant("2"), grant("3"), grant("4"), grant("5"),
                grant("6"), grant("7"), grant("8"), grant("9"), grant("10"));
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        PortfolioService service = service(grants, 10, q -> {
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            sleep(100);
            inFlight.decrementAndGet();
            return new ArticleSearchResult(0, List.of());
        }, Duration.ofSeconds(5));

        long start = System.nanoTime();
        service.build("University of Cambridge", 10, 1);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(maxInFlight.get()).isEqualTo(POOL_SIZE);
        // 10 lookups x 100 ms run in ceil(10/3) = 4 waves, well under the sequential 1000 ms.
        assertThat(elapsedMs).isLessThan(800);
    }

    @Test
    void lookupsStillRunningAtTheDeadlineAreReportedAsFailed() {
        List<GrantRecord> grants = List.of(grant("fast"), grant("slow"));
        PortfolioService service = service(grants, 2, q -> {
            if (q.contains("slow")) {
                sleep(2_000);
            }
            return confirmedFor(idFromQuery(q), "x");
        }, Duration.ofMillis(300));

        PortfolioResponse response = service.build("University of Cambridge", 10, 3);

        assertThat(response.grants().get(0).publicationLookup().status()).isEqualTo(LookupStatus.FOUND);
        assertThat(response.grants().get(1).publicationLookup().status()).isEqualTo(LookupStatus.LOOKUP_FAILED);
        assertThat(response.grants().get(1).publicationLookup().error()).contains("did not finish");
    }

    @Test
    void grantWithoutIdIsNotLookedUp() {
        Map<String, Integer> calls = new ConcurrentHashMap<>();
        PortfolioService service = service(List.of(grant(null)), 1, q -> {
            calls.merge(q, 1, Integer::sum);
            return new ArticleSearchResult(0, List.of());
        }, Duration.ofSeconds(5));

        PortfolioResponse response = service.build("University of Cambridge", 10, 3);

        assertThat(calls).isEmpty();
        assertThat(response.grants().get(0).publicationLookup().status()).isEqualTo(LookupStatus.NOT_ATTEMPTED);
    }

    @Test
    void emptyInstitutionSearchIsASuccessfulEmptyResponse() {
        PortfolioService service = service(List.of(), 0, q -> {
            throw new AssertionError("no lookups expected");
        }, Duration.ofSeconds(5));

        PortfolioResponse response = service.build("Nowhere University", 5, 3);

        assertThat(response.grants()).isEmpty();
        assertThat(response.grantSearch().truncated()).isFalse();
        assertThat(response.caveats().get(0)).contains("No grants matched");
    }

    @Test
    void limitsGrantsFlagsTruncationAndSummarisesOnlyTheSample() {
        List<GrantRecord> grants = List.of(grant("A"), grant("B"), grant("C"));
        // The same article is linked to both A and B: kept under each grant, counted once as unique.
        PortfolioService service = service(grants, 500, q -> new ArticleSearchResult(1, List.of(
                new ArticleRecord("MED", "shared", "shared", null, null, "T", "J", "2021",
                        List.of(new FundingReference("A", "Wellcome Trust"), new FundingReference("B", "Wellcome Trust"))))),
                Duration.ofSeconds(5));

        PortfolioResponse response = service.build("University of Cambridge", 2, 3);

        assertThat(response.grants()).extracting(GrantEntry::id).containsExactly("A", "B");
        assertThat(response.grantSearch().truncated()).isTrue();
        assertThat(response.grantSearch().totalHitsReported()).isEqualTo(500);
        assertThat(response.summary().grantsByFunder()).containsExactly(Map.entry("Wellcome Trust", 2));
        assertThat(response.summary().publicationAssociations()).isEqualTo(2);
        assertThat(response.summary().uniquePublications()).isEqualTo(1);
        assertThat(response.grants().get(0).institution().exactNameMatch()).isTrue();
    }

    @Test
    void grantSearchFailurePropagates() {
        GrantsClient failing = q -> {
            throw new UpstreamException("Europe PMC Grants API", "Europe PMC Grants API returned HTTP 500", false, null);
        };
        PortfolioService service = new PortfolioService(failing, (q, s) -> null, pool,
                new PortfolioProperties("x", "y", Duration.ofSeconds(1), Duration.ofSeconds(1), 3, Duration.ofSeconds(1)));

        assertThatThrownBy(() -> service.build("X University", 5, 3)).isInstanceOf(UpstreamException.class);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
