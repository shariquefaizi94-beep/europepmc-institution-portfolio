package com.example.portfolio.model;

/**
 * How an article's own grantsList relates to the grant it was found under. Grant IDs are not
 * unique across funders, so a GRANT_ID hit alone is not treated as verified.
 */
public enum FundingMatch {
    /** The article lists this grant ID with the same funder. */
    FUNDER_CONFIRMED,
    /** The article lists this grant ID but gives no funder for it. */
    FUNDER_NOT_REPORTED,
    /** The article lists this grant ID only under a different funder. */
    FUNDER_DIFFERS,
    /** The search matched, but this grant ID is not in the article's grantsList as returned. */
    GRANT_NOT_LISTED
}
