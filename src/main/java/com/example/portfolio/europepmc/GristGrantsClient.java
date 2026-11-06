package com.example.portfolio.europepmc;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import com.example.portfolio.config.PortfolioProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Client for the Europe PMC Grants RESTful (Grist) API.
 *
 * <p>Grist takes its parameters in the path rather than a query string:
 * {@code /get/query=<q>&resultType=core&format=json}. The query is percent-encoded as a whole
 * (including spaces, quotes, {@code &}, {@code /} and {@code =}) so user text cannot break out of
 * the {@code query=} parameter.
 */
@Component
public class GristGrantsClient implements GrantsClient {

    static final String UPSTREAM = "Europe PMC Grants API";

    private final EuropePmcHttp http;
    private final String baseUrl;

    public GristGrantsClient(EuropePmcHttp http, PortfolioProperties props) {
        this.http = http;
        this.baseUrl = stripTrailingSlash(props.grantsBaseUrl());
    }

    @Override
    public GrantSearchResult search(String gristQuery) {
        JsonNode root = http.getJson(buildUri(gristQuery), UPSTREAM);
        return parse(root);
    }

    URI buildUri(String gristQuery) {
        String encoded = UriUtils.encode(gristQuery, StandardCharsets.UTF_8);
        return URI.create(baseUrl + "/get/query=" + encoded + "&resultType=core&format=json");
    }

    static GrantSearchResult parse(JsonNode root) {
        Integer hitCount = Json.integer(root, "HitCount");
        JsonNode recordList = root.path("RecordList");
        List<GrantRecord> grants = Json.list(recordList.get("Record")).stream()
                .map(GristGrantsClient::toGrant)
                .toList();
        return new GrantSearchResult(hitCount, grants);
    }

    private static GrantRecord toGrant(JsonNode record) {
        JsonNode grant = record.path("Grant");
        JsonNode institution = record.path("Institution");
        return new GrantRecord(
                Json.text(grant, "Id"),
                Json.text(grant, "Title"),
                Json.text(grant, "Funder", "Name"),
                Json.text(institution, "Name"),
                Json.text(institution, "Department"));
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
