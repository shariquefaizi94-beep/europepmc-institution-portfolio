package com.example.portfolio.europepmc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QuerySyntaxTest {

    @Test
    void wrapsInstitutionInQuotedAffiliationPhrase() {
        assertThat(QuerySyntax.affiliationQuery("University of Cambridge"))
                .isEqualTo("aff:\"University of Cambridge\"");
    }

    @Test
    void collapsesWhitespace() {
        assertThat(QuerySyntax.affiliationQuery("  University   of\tManchester "))
                .isEqualTo("aff:\"University of Manchester\"");
    }

    @Test
    void escapesQuotesSoInputCannotLeaveThePhrase() {
        // Without escaping this would become: aff:"Cambridge" OR aff:"Oxford"
        assertThat(QuerySyntax.affiliationQuery("Cambridge\" OR aff:\"Oxford"))
                .isEqualTo("aff:\"Cambridge\\\" OR aff:\\\"Oxford\"");
    }

    @Test
    void escapesBackslashes() {
        assertThat(QuerySyntax.affiliationQuery("A\\B")).isEqualTo("aff:\"A\\\\B\"");
    }

    @Test
    void leavesOtherQuerySyntaxInertInsidePhrase() {
        assertThat(QuerySyntax.affiliationQuery("King's College (London) & *"))
                .isEqualTo("aff:\"King's College (London) & *\"");
    }

    @Test
    void buildsGrantIdQuery() {
        assertThat(QuerySyntax.grantIdQuery("MR/R006504/1")).isEqualTo("GRANT_ID:\"MR/R006504/1\"");
    }
}
