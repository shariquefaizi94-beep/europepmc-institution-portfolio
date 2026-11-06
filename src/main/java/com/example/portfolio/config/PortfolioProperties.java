package com.example.portfolio.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All tunables in one place, bound from {@code application.yml} (prefix {@code portfolio}).
 *
 * @param grantsBaseUrl        base URL of the Europe PMC Grants (Grist) REST API
 * @param articlesBaseUrl      base URL of the Europe PMC Articles REST API
 * @param connectTimeout       TCP connect timeout for each upstream call
 * @param readTimeout          socket read timeout for each upstream call
 * @param maxConcurrentLookups size of the shared pool used for publication lookups; this is a
 *                             global cap across all incoming requests, not per request
 * @param lookupDeadline       how long one portfolio request waits in total for its
 *                             publication lookups before marking the stragglers as failed
 */
@ConfigurationProperties(prefix = "portfolio")
public record PortfolioProperties(
        String grantsBaseUrl,
        String articlesBaseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        int maxConcurrentLookups,
        Duration lookupDeadline) {
}
