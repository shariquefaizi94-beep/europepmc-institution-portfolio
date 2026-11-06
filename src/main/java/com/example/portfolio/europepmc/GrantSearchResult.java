package com.example.portfolio.europepmc;

import java.util.List;

public record GrantSearchResult(Integer hitCount, List<GrantRecord> grants) {
}
