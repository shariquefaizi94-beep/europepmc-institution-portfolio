package com.example.portfolio.europepmc;

import java.net.URI;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.portfolio.config.PortfolioProperties;
import com.example.portfolio.europepmc.ArticleRecord.FundingReference;
import com.fasterxml.jackson.databind.JsonNode;

// resultType=core because only the core view has grantsList, which we need for funder checks.
@Component
public class EuropePmcArticlesClient implements ArticlesClient {

    static final String UPSTREAM = "Europe PMC Articles API";

    private final EuropePmcHttp http;
    private final String baseUrl;

    public EuropePmcArticlesClient(EuropePmcHttp http, PortfolioProperties props) {
        this.http = http;
        this.baseUrl = props.articlesBaseUrl();
    }

    @Override
    public ArticleSearchResult search(String query, int pageSize) {
        return parse(http.getJson(buildUri(query, pageSize), UPSTREAM));
    }

    URI buildUri(String query, int pageSize) {
        // The query goes in as a URI variable so it is strictly encoded: reserved characters
        // such as '+', '&' and '=' in a grant ID are percent-encoded rather than passed through.
        return UriComponentsBuilder.fromUriString(baseUrl)
                .path("/search")
                .queryParam("query", "{query}")
                .queryParam("resultType", "core")
                .queryParam("format", "json")
                .queryParam("pageSize", pageSize)
                .encode()
                .buildAndExpand(query)
                .toUri();
    }

    static ArticleSearchResult parse(JsonNode root) {
        Integer hitCount = Json.integer(root, "hitCount");
        List<ArticleRecord> articles = Json.list(root.path("resultList").get("result")).stream()
                .map(EuropePmcArticlesClient::toArticle)
                .toList();
        return new ArticleSearchResult(hitCount, articles);
    }

    private static ArticleRecord toArticle(JsonNode r) {
        String journal = Json.text(r, "journalInfo", "journal", "title");
        if (journal == null) {
            journal = Json.text(r, "journalTitle"); // present in the lite view
        }
        String year = Json.text(r, "pubYear");
        if (year == null) {
            year = Json.text(r, "journalInfo", "yearOfPublication");
        }
        List<FundingReference> funding = Json.list(r.path("grantsList").get("grant")).stream()
                .map(g -> new FundingReference(Json.text(g, "grantId"), Json.text(g, "agency")))
                .toList();
        return new ArticleRecord(
                Json.text(r, "source"),
                Json.text(r, "id"),
                Json.text(r, "pmid"),
                Json.text(r, "pmcid"),
                Json.text(r, "doi"),
                Json.text(r, "title"),
                journal,
                year,
                funding);
    }
}
