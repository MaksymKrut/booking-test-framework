package com.booking.tests.api.model;

import java.time.Instant;

/**
 * Body of {@code POST/PUT /shifts}. {@code Instant} is written as ISO-8601 UTC ({@code ...Z}), the format
 * the server expects. All fields are nullable so validation cases can send explicit nulls.
 */
public record ShiftRequest(String zone, Instant startTime, Instant endTime, Integer capacity) {
}
