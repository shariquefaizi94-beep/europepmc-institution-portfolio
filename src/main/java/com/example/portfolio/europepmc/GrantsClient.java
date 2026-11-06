package com.example.portfolio.europepmc;

public interface GrantsClient {

    // first results page only; throws UpstreamException
    GrantSearchResult search(String gristQuery);
}
