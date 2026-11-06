package com.example.portfolio.model;

public enum LookupStatus {
    FOUND,          // at least one publication confirms this grant ID + funder
    NONE_FOUND,     // Articles API answered with zero hits (doesn't prove there are none)
    UNCERTAIN,      // hits for the ID, but none confirm the funder
    LOOKUP_FAILED,  // call failed / timed out - we know nothing
    NOT_ATTEMPTED   // grant record had no ID
}
