package com.example.portfolio.europepmc;

/** One award as returned by the Grants (Grist) API. Any field may be null. */
public record GrantRecord(
        String id,
        String title,
        String funder,
        String institutionName,
        String department) {
}
