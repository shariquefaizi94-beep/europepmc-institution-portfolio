package com.example.portfolio.europepmc;

public interface ArticlesClient {

    /**
     * Runs an Articles API query and returns up to {@code pageSize} results.
     *
     * @throws UpstreamException if the call fails or the response cannot be read
     */
    ArticleSearchResult search(String query, int pageSize);
}
