package com.example.portfolio.model;

// How an article's own grantsList relates to the grant we searched for.
// Grant IDs aren't unique across funders, so an ID hit alone isn't treated as verified.
public enum FundingMatch {
    FUNDER_CONFIRMED,
    FUNDER_NOT_REPORTED,
    FUNDER_DIFFERS,
    GRANT_NOT_LISTED
}
