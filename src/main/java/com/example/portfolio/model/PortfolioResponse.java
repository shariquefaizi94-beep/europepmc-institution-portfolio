package com.example.portfolio.model;

import java.util.List;
import java.util.Map;

// Whole response contract in one place. Field meanings are documented in the README.
public record PortfolioResponse(
        RequestEcho request,
        GrantSearch grantSearch,
        List<GrantEntry> grants,
        Summary summary,
        List<String> caveats) {

    public record RequestEcho(String institution, int limit, int publicationsPerGrant, String grantsQuery) {
    }

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

    // exactNameMatch = false means Grist matched a variant (hospital trust, department name, ...)
    public record Institution(String name, String department, boolean exactNameMatch) {
    }

    public record PublicationLookup(
            LookupStatus status,
            String query,
            Integer totalHitsReported,
            int returned,
            String error) {
    }

    public record Publication(
            String key, // "<source>:<id>", e.g. MED:12345678
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

    public record Summary(
            String scope,
            int grantsReturned,
            Map<String, Integer> grantsByFunder,
            Map<LookupStatus, Integer> grantsByLookupStatus,
            int publicationAssociations,
            int uniquePublications) {
    }
}
