package com.booking.tests.api.model;

/**
 * Delivery zones as published by {@code GET /meta}. Request records carry the zone as a plain
 * {@code String} (so negative tests can send values outside this enum); builders and assertions
 * use {@link #name()}.
 */
public enum Zone {
    DOWNTOWN, NORTH, SOUTH, EAST, WEST
}
