package com.example.portfolio.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "portfolio")
public record PortfolioProperties(
        String grantsBaseUrl,
        String articlesBaseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        int maxConcurrentLookups, // global across all requests, not per request
        Duration lookupDeadline) {
}
