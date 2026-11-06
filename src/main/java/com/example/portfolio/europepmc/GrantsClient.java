package com.example.portfolio.europepmc;

public interface GrantsClient {

    /**
     * Runs a Grist query and returns the first results page.
     *
     * @throws UpstreamException if the call fails or the response cannot be read
     */
    GrantSearchResult search(String gristQuery);
}
