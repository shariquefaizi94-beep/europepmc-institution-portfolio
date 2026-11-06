package com.example.portfolio.europepmc;

// Any field may be null.
public record GrantRecord(
        String id,
        String title,
        String funder,
        String institutionName,
        String department) {
}
