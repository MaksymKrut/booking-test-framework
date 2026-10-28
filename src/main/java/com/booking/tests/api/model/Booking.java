package com.booking.tests.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * {@code BookingResponse}. {@code status} is {@code CONFIRMED} or {@code CANCELLED}; {@code cancelledAt}
 * is always present on the wire and {@code null} until the booking is cancelled.
 * Timestamps are parsed as {@link Instant} because the server's fraction digits vary (none, 3, 6, 9).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Booking(Long id,
                      Long courierId,
                      String courierName,
                      Long shiftId,
                      String zone,
                      Instant shiftStart,
                      Instant shiftEnd,
                      String status,
                      Instant createdAt,
                      Instant cancelledAt) {
}
