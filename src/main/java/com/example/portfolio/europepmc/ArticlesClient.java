package com.example.portfolio.europepmc;

public interface ArticlesClient {

    // throws UpstreamException
    ArticleSearchResult search(String query, int pageSize);
}
