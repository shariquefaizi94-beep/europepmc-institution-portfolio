package com.example.portfolio.model;

import java.util.List;
import java.util.Map;

/**
 * Response of {@code GET /institution-portfolio}. Nested records keep the whole JSON contract in
 * one file so it is easy to review.
 */
public record PortfolioResponse(
        RequestEcho request,
        GrantSearch grantSearch,
        List<GrantEntry> grants,
        Summary summary,
        List<String> caveats) {

    /** What was asked for, and the exact upstream query that was run. */
    public record RequestEcho(
            String institution,
            int limit,
            int publicationsPerGrant,
            String grantsQuery) {
    }

    /**
     * @param totalHitsReported total Grist matches (may be far more than returned)
     * @param returned          grants included in this response
     * @param truncated         true if Grist reported more matches than were returned
     */
    public record GrantSearch(Integer totalHitsReported, int returned, boolean truncated) {
    }

    public record GrantEntry(
            String id,
            String title,
            String funder,
            Institution institution,
            PublicationLookup publicationLookup,
            List<Publication> publications) {
    }

    /**
     * @param exactNameMatch whether the institution name on the grant equals the requested name
     *                       (case- and punctuation-insensitive). False means Grist matched a
     *                       variant, e.g. a hospital trust or a department-level name.
     */
    public record Institution(String name, String department, boolean exactNameMatch) {
    }

    /**
     * @param status            outcome of the publication lookup for this grant
     * @param query             the Articles API query that was run (null if not attempted)
     * @param totalHitsReported total Articles API matches for that query
     * @param returned          publications included for this grant
     * @param error             failure reason when status is LOOKUP_FAILED, else null
     */
    public record PublicationLookup(
            LookupStatus status,
            String query,
            Integer totalHitsReported,
            int returned,
            String error) {
    }

    /**
     * @param key         stable identity: {@code <source>:<id>}, e.g. {@code MED:12345678}
     * @param fundingMatch how the article's own funding metadata relates to this grant
     */
    public record Publication(
            String key,
            String source,
            String id,
            String pmid,
            String pmcid,
            String doi,
            String title,
            String journal,
            String pubYear,
            FundingMatch fundingMatch) {
    }

    /**
     * Counts describe this response only, never the institution's full portfolio.
     */
    public record Summary(
            String scope,
            int grantsReturned,
            Map<String, Integer> grantsByFunder,
            Map<LookupStatus, Integer> grantsByLookupStatus,
            int publicationAssociations,
            int uniquePublications) {
    }
}
