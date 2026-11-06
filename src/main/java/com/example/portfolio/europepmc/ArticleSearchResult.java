package com.example.portfolio.europepmc;

import java.util.List;

public record ArticleSearchResult(Integer hitCount, List<ArticleRecord> articles) {
}
