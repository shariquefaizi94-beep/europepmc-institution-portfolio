package com.example.portfolio.europepmc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.example.portfolio.europepmc.ArticleRecord.FundingReference;

class EuropePmcArticlesClientTest {

    private final StubServer stub = new StubServer();
    private final EuropePmcArticlesClient client =
            new EuropePmcArticlesClient(stub.http(Duration.ofSeconds(2)), stub.props(Duration.ofSeconds(2)));

    @AfterEach
    void stop() {
        stub.close();
    }

    @Test
    void strictlyEncodesQueryIncludingPlusAndAmpersand() {
        stub.body = "{\"hitCount\":0,\"resultList\":{\"result\":[]}}";

        client.search(QuerySyntax.grantIdQuery("AB+12&C=3"), 3);

        assertThat(stub.rawRequestUris.get(0)).isEqualTo(
                "/search?query=GRANT_ID%3A%22AB%2B12%26C%3D3%22&resultType=core&format=json&pageSize=3");
    }

    /** Fixture is a trimmed copy of a real Articles API response for GRANT_ID:"088130". */
    @Test
    void parsesRealResponseShape() {
        stub.respondWithFixture("articles-grant-088130.json");

        ArticleSearchResult result = client.search("GRANT_ID:\"088130\"", 2);

        assertThat(result.hitCount()).isEqualTo(337);
        assertThat(result.articles()).hasSize(2);
        ArticleRecord first = result.articles().get(0);
        assertThat(first.source()).isEqualTo("MED");
        assertThat(first.id()).isEqualTo("34113717");
        assertThat(first.doi()).isEqualTo("10.5334/cpsy.70");
        assertThat(first.journal()).isEqualTo("Computational psychiatry (Cambridge, Mass.)");
        assertThat(first.pubYear()).isEqualTo("2021");
        assertThat(first.fundingReferences())
                .contains(new FundingReference("088130", "Wellcome Trust"),
                        new FundingReference("088130/Z/09/Z", "Wellcome Trust"));
        assertThat(result.articles().get(1).source()).isEqualTo("PPR");
    }
}
