package com.example.portfolio.europepmc;

import java.util.List;

/**
 * One publication from the Articles API ({@code resultType=core}).
 *
 * @param fundingReferences the article's own grantsList, used to check the grant/funder pairing
 */
public record ArticleRecord(
        String source,
        String id,
        String pmid,
        String pmcid,
        String doi,
        String title,
        String journal,
        String pubYear,
        List<FundingReference> fundingReferences) {

    /** One entry of an article's grantsList. */
    public record FundingReference(String grantId, String agency) {
    }
}
