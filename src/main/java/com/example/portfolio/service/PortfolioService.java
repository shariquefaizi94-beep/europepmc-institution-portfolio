package com.example.portfolio.service;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.portfolio.config.PortfolioProperties;
import com.example.portfolio.europepmc.ArticleSearchResult;
import com.example.portfolio.europepmc.ArticlesClient;
import com.example.portfolio.europepmc.GrantRecord;
import com.example.portfolio.europepmc.GrantSearchResult;
import com.example.portfolio.europepmc.GrantsClient;
import com.example.portfolio.europepmc.QuerySyntax;
import com.example.portfolio.europepmc.UpstreamException;
import com.example.portfolio.model.LookupStatus;
import com.example.portfolio.model.PortfolioResponse;
import com.example.portfolio.model.PortfolioResponse.GrantEntry;
import com.example.portfolio.model.PortfolioResponse.GrantSearch;
import com.example.portfolio.model.PortfolioResponse.Institution;
import com.example.portfolio.model.PortfolioResponse.Publication;
import com.example.portfolio.model.PortfolioResponse.PublicationLookup;
import com.example.portfolio.model.PortfolioResponse.RequestEcho;
import com.example.portfolio.model.PortfolioResponse.Summary;

/**
 * Builds a portfolio in two phases:
 * <ol>
 *   <li>one Grist call to find grants for the institution (a failure here fails the request);</li>
 *   <li>one Articles API call per grant, run concurrently on a bounded shared pool. Each lookup
 *       fails on its own: a failure is recorded on that grant and the others are unaffected.</li>
 * </ol>
 * Results are collected by position, so the output keeps Grist order whatever order the lookups
 * finish in.
 */
@Service
public class PortfolioService {

    private static final Logger log = LoggerFactory.getLogger(PortfolioService.class);

    static final List<String> STANDARD_CAVEATS = List.of(
            "Grants come from the Europe PMC Grants API, which only indexes awards from participating funders.",
            "Institution matching is a text search on the award holder's affiliation; name variants may be "
                    + "missed or unrelated institutions with similar names included.",
            "Only the first page of grant results is used, so this is a sample, not the institution's full portfolio.",
            "NONE_FOUND means no linked publications were found in Europe PMC, not that the grant produced none.",
            "Summary counts describe this response only.");

    private final GrantsClient grantsClient;
    private final ArticlesClient articlesClient;
    private final ExecutorService lookupExecutor;
    private final PortfolioProperties props;

    public PortfolioService(GrantsClient grantsClient, ArticlesClient articlesClient,
                            ExecutorService publicationLookupExecutor, PortfolioProperties props) {
        this.grantsClient = grantsClient;
        this.articlesClient = articlesClient;
        this.lookupExecutor = publicationLookupExecutor;
        this.props = props;
    }

    /**
     * @throws UpstreamException if the grant search itself fails
     */
    public PortfolioResponse build(String institution, int limit, int publicationsPerGrant) {
        String grantsQuery = QuerySyntax.affiliationQuery(institution);
        GrantSearchResult search = grantsClient.search(grantsQuery);
        List<GrantRecord> grants = search.grants().stream().limit(limit).toList();

        // Start every lookup before waiting on any of them, so they overlap.
        List<CompletableFuture<GrantEntry>> futures = grants.stream()
                .map(g -> startLookup(g, publicationsPerGrant, institution))
                .toList();

        List<GrantEntry> entries = collectInOrder(grants, futures, institution);

        boolean truncated = search.hitCount() != null && search.hitCount() > grants.size();
        List<String> caveats = new ArrayList<>(STANDARD_CAVEATS);
        if (truncated) {
            caveats.add(0, "The Grants API reported " + search.hitCount() + " matching records; only the first "
                    + grants.size() + " are included.");
        }
        if (grants.isEmpty()) {
            caveats.add(0, "No grants matched this institution name. Try a different form of the name "
                    + "(e.g. 'University of Cambridge' vs 'Cambridge University').");
        }

        return new PortfolioResponse(
                new RequestEcho(institution, limit, publicationsPerGrant, grantsQuery),
                new GrantSearch(search.hitCount(), grants.size(), truncated),
                entries,
                summarise(entries),
                caveats);
    }

    private CompletableFuture<GrantEntry> startLookup(GrantRecord grant, int perGrant, String requested) {
        if (grant.id() == null) {
            return CompletableFuture.completedFuture(entry(grant, requested,
                    new PublicationLookup(LookupStatus.NOT_ATTEMPTED, null, null, 0,
                            "Grant record has no ID"), List.of()));
        }
        return CompletableFuture.supplyAsync(() -> lookup(grant, perGrant, requested), lookupExecutor);
    }

    /** Runs on a pool thread. Never throws: every failure becomes a LOOKUP_FAILED entry. */
    private GrantEntry lookup(GrantRecord grant, int perGrant, String requested) {
        String query = QuerySyntax.grantIdQuery(grant.id());
        try {
            ArticleSearchResult result = articlesClient.search(query, perGrant);
            List<Publication> pubs = GrantLinker.toPublications(grant, result, perGrant);
            LookupStatus status = GrantLinker.statusFor(pubs);
            return entry(grant, requested,
                    new PublicationLookup(status, query, result.hitCount(), pubs.size(), null), pubs);
        } catch (UpstreamException e) {
            log.warn("Publication lookup failed for grant {}: {}", grant.id(), e.getMessage());
            return failed(grant, requested, query, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Unexpected error looking up grant {}", grant.id(), e);
            return failed(grant, requested, query, "Unexpected error during publication lookup");
        }
    }

    /**
     * Waits for each future in grant order against one shared deadline. A lookup still queued or
     * running when the deadline passes is cancelled and reported as failed; a cancelled task that
     * has not started yet is never run, so an abandoned request does not keep calling Europe PMC.
     */
    private List<GrantEntry> collectInOrder(List<GrantRecord> grants, List<CompletableFuture<GrantEntry>> futures,
                                            String requested) {
        long deadline = System.nanoTime() + props.lookupDeadline().toNanos();
        List<GrantEntry> entries = new ArrayList<>(grants.size());
        for (int i = 0; i < grants.size(); i++) {
            GrantRecord grant = grants.get(i);
            CompletableFuture<GrantEntry> future = futures.get(i);
            String query = grant.id() == null ? null : QuerySyntax.grantIdQuery(grant.id());
            try {
                long remaining = Math.max(0, deadline - System.nanoTime());
                entries.add(future.get(remaining, TimeUnit.NANOSECONDS));
            } catch (TimeoutException e) {
                future.cancel(true);
                entries.add(failed(grant, requested, query, "Publication lookup did not finish within "
                        + props.lookupDeadline().toSeconds() + "s"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                entries.add(failed(grant, requested, query, "Interrupted"));
            } catch (ExecutionException e) {
                entries.add(failed(grant, requested, query, "Unexpected error during publication lookup"));
            }
        }
        return entries;
    }

    private static GrantEntry failed(GrantRecord grant, String requested, String query, String error) {
        return entry(grant, requested, new PublicationLookup(LookupStatus.LOOKUP_FAILED, query, null, 0, error),
                List.of());
    }

    private static GrantEntry entry(GrantRecord grant, String requested, PublicationLookup lookup,
                                    List<Publication> pubs) {
        boolean exact = Objects.equals(GrantLinker.normaliseName(grant.institutionName()),
                GrantLinker.normaliseName(requested));
        return new GrantEntry(grant.id(), grant.title(), grant.funder(),
                new Institution(grant.institutionName(), grant.department(), exact), lookup, pubs);
    }

    static Summary summarise(List<GrantEntry> entries) {
        Map<String, Integer> byFunder = new LinkedHashMap<>();
        Map<LookupStatus, Integer> byStatus = new EnumMap<>(LookupStatus.class);
        Set<String> uniqueKeys = new HashSet<>();
        int associations = 0;
        for (GrantEntry e : entries) {
            byFunder.merge(e.funder() == null ? "(funder not reported)" : e.funder(), 1, Integer::sum);
            byStatus.merge(e.publicationLookup().status(), 1, Integer::sum);
            associations += e.publications().size();
            e.publications().forEach(p -> uniqueKeys.add(p.key() != null ? p.key() : "anon-" + System.identityHashCode(p)));
        }
        return new Summary(
                "Counts cover only the grants and publications in this response, not the institution's full portfolio.",
                entries.size(), byFunder, byStatus, associations, uniqueKeys.size());
    }
}
