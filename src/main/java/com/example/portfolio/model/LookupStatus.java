package com.example.portfolio.model;

/** Outcome of looking up publications for one grant. */
public enum LookupStatus {
    /** At least one returned publication lists this grant ID under the same funder. */
    FOUND,
    /** The Articles API returned no publications for this grant ID. Not proof that none exist. */
    NONE_FOUND,
    /**
     * Publications were returned for this grant ID, but none of them confirm the funder, so the
     * link may belong to a different funder's grant with the same ID.
     */
    UNCERTAIN,
    /** The Articles API call failed or timed out. Says nothing about whether publications exist. */
    LOOKUP_FAILED,
    /** The grant record has no ID, so there was nothing to look up. */
    NOT_ATTEMPTED
}
