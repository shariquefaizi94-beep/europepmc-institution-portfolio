package com.example.portfolio.europepmc;

import java.util.List;

/**
 * @param hitCount total matches the Articles API reports (null if not reported)
 * @param articles first page of results, in API order
 */
public record ArticleSearchResult(Integer hitCount, List<ArticleRecord> articles) {
}
