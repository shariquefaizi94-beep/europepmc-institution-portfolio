package com.example.portfolio.europepmc;

import java.util.List;

/**
 * @param hitCount total matches Grist reports for the query (null if not reported)
 * @param grants   records from the first results page, in Grist order
 */
public record GrantSearchResult(Integer hitCount, List<GrantRecord> grants) {
}
