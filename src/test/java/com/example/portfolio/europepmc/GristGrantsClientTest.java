package com.example.portfolio.europepmc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GristGrantsClientTest {

    private final StubServer stub = new StubServer();
    private final GristGrantsClient client =
            new GristGrantsClient(stub.http(Duration.ofSeconds(2)), stub.props(Duration.ofSeconds(2)));

    @AfterEach
    void stop() {
        stub.close();
    }

    @Test
    void percentEncodesTheWholeQueryIntoThePath() {
        stub.respondWithFixture("grist-cambridge.json");

        client.search(QuerySyntax.affiliationQuery("Imperial College London & \"Friends\"/Partners"));

        String uri = stub.rawRequestUris.get(0);
        assertThat(uri).startsWith("/get/query=aff%3A%22Imperial%20College%20London%20%26%20%5C%22Friends%5C%22%2FPartners%22");
        assertThat(uri).endsWith("&resultType=core&format=json");
    }

    @Test
    void parsesRecordsInOrderAndToleratesMissingFields() {
        stub.respondWithFixture("grist-cambridge.json");

        GrantSearchResult result = client.search("aff:\"University of Cambridge\"");

        assertThat(result.hitCount()).isEqualTo(3127);
        assertThat(result.grants()).extracting(GrantRecord::id)
                .containsExactly("088130", "MR/R006504/1", null);
        GrantRecord first = result.grants().get(0);
        assertThat(first.funder()).isEqualTo("Wellcome Trust");
        assertThat(first.institutionName()).isEqualTo("University of Cambridge");
        assertThat(first.department()).isEqualTo("Department of Psychology");
        GrantRecord sparse = result.grants().get(2);
        assertThat(sparse.funder()).isNull();
        assertThat(sparse.institutionName()).isNull();
    }

    @Test
    void handlesSingleRecordReturnedAsObjectRatherThanArray() {
        stub.respondWithFixture("grist-single-record.json");

        GrantSearchResult result = client.search("aff:\"University of Manchester\"");

        assertThat(result.hitCount()).isEqualTo(1);
        assertThat(result.grants()).singleElement().satisfies(g -> {
            assertThat(g.id()).isEqualTo("203147");
            assertThat(g.funder()).isEqualTo("Wellcome Trust");
            assertThat(g.title()).isEqualTo("Title carried as XML text content");
        });
    }

    @Test
    void emptyResultIsNotAnError() {
        stub.body = "{\"HitCount\":0,\"RecordList\":{}}";

        GrantSearchResult result = client.search("aff:\"Nowhere University\"");

        assertThat(result.hitCount()).isZero();
        assertThat(result.grants()).isEmpty();
    }

    @Test
    void serverErrorBecomesNonTimeoutUpstreamException() {
        stub.status = 503;

        assertThatThrownBy(() -> client.search("aff:\"X\""))
                .isInstanceOfSatisfying(UpstreamException.class, e -> {
                    assertThat(e.isTimeout()).isFalse();
                    assertThat(e.getMessage()).contains("HTTP 503");
                });
    }

    @Test
    void invalidJsonBecomesUpstreamException() {
        stub.body = "<html>maintenance</html>";

        assertThatThrownBy(() -> client.search("aff:\"X\""))
                .isInstanceOf(UpstreamException.class)
                .hasMessageContaining("not valid JSON");
    }

    @Test
    void slowResponseBecomesTimeout() {
        stub.delayMillis = 1500;
        GristGrantsClient impatient = new GristGrantsClient(
                stub.http(Duration.ofMillis(200)), stub.props(Duration.ofMillis(200)));

        assertThatThrownBy(() -> impatient.search("aff:\"X\""))
                .isInstanceOfSatisfying(UpstreamException.class, e -> assertThat(e.isTimeout()).isTrue());
    }
}
