package com.example.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.portfolio.europepmc.ArticleRecord;
import com.example.portfolio.europepmc.ArticleRecord.FundingReference;
import com.example.portfolio.europepmc.ArticleSearchResult;
import com.example.portfolio.europepmc.GrantRecord;
import com.example.portfolio.model.FundingMatch;
import com.example.portfolio.model.LookupStatus;
import com.example.portfolio.model.PortfolioResponse.Publication;

class GrantLinkerTest {

    private static final GrantRecord WELLCOME_GRANT =
            new GrantRecord("088130", "t", "Wellcome Trust", "University of Cambridge", null);

    static ArticleRecord article(String id, FundingReference... refs) {
        return new ArticleRecord("MED", id, id, null, null, "Title " + id, "J", "2020", List.of(refs));
    }

    @Test
    void sameIdAndFunderIsConfirmed() {
        assertThat(GrantLinker.classify(WELLCOME_GRANT,
                article("1", new FundingReference("088130", "Wellcome Trust"))))
                .isEqualTo(FundingMatch.FUNDER_CONFIRMED);
    }

    @Test
    void wellcomeStyleSuffixCountsAsSameGrant() {
        assertThat(GrantLinker.classify(WELLCOME_GRANT,
                article("1", new FundingReference("088130/Z/09/Z", "Wellcome"))))
                .isEqualTo(FundingMatch.FUNDER_CONFIRMED);
    }

    @Test
    void sameIdFromAnotherFunderIsNotTreatedAsVerified() {
        assertThat(GrantLinker.classify(WELLCOME_GRANT,
                article("1", new FundingReference("088130", "National Institutes of Health"))))
                .isEqualTo(FundingMatch.FUNDER_DIFFERS);
    }

    @Test
    void missingAgencyIsReportedAsSuch() {
        assertThat(GrantLinker.classify(WELLCOME_GRANT, article("1", new FundingReference("088130", null))))
                .isEqualTo(FundingMatch.FUNDER_NOT_REPORTED);
    }

    @Test
    void searchHitWithoutTheIdInGrantsListIsFlagged() {
        assertThat(GrantLinker.classify(WELLCOME_GRANT, article("1", new FundingReference("999999", "Wellcome Trust"))))
                .isEqualTo(FundingMatch.GRANT_NOT_LISTED);
        assertThat(GrantLinker.classify(WELLCOME_GRANT, article("2")))
                .isEqualTo(FundingMatch.GRANT_NOT_LISTED);
    }

    @Test
    void shortPrefixDoesNotMatchLongerStructuredId() {
        assertThat(GrantLinker.sameGrantId("MR", "MR/K006584/1")).isFalse();
        assertThat(GrantLinker.sameGrantId("mr/k006584/1", "MR/K006584/1")).isTrue();
    }

    @Test
    void statusIsFoundOnlyWithAConfirmedPublication() {
        GrantRecord g = WELLCOME_GRANT;
        List<Publication> confirmed = GrantLinker.toPublications(g, new ArticleSearchResult(1,
                List.of(article("1", new FundingReference("088130", "Wellcome Trust")))), 5);
        List<Publication> ambiguous = GrantLinker.toPublications(g, new ArticleSearchResult(1,
                List.of(article("1", new FundingReference("088130", "Other Funder")))), 5);

        assertThat(GrantLinker.statusFor(confirmed)).isEqualTo(LookupStatus.FOUND);
        assertThat(GrantLinker.statusFor(ambiguous)).isEqualTo(LookupStatus.UNCERTAIN);
        assertThat(GrantLinker.statusFor(List.of())).isEqualTo(LookupStatus.NONE_FOUND);
    }

    @Test
    void keepsApiOrderCapsCountAndDedupesWithinAGrant() {
        ArticleRecord a = article("1");
        ArticleRecord b = article("2");
        ArticleRecord c = article("3");
        List<Publication> pubs = GrantLinker.toPublications(WELLCOME_GRANT,
                new ArticleSearchResult(4, List.of(b, a, b, c)), 2);

        assertThat(pubs).extracting(Publication::key).containsExactly("MED:2", "MED:1");
    }

    @Test
    void publicationKeyFallsBackToDoi() {
        ArticleRecord noId = new ArticleRecord(null, null, null, null, "10.1/ABC", "t", null, null, List.of());
        assertThat(GrantLinker.publicationKey(noId)).isEqualTo("DOI:10.1/abc");
    }
}
