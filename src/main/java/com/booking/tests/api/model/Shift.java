package com.booking.tests.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * {@code ShiftResponse}. {@code bookedCount} counts CONFIRMED bookings only and
 * {@code availableSlots = capacity - bookedCount}. {@code status} is derived by the server
 * ({@code OPEN}, {@code FULL}, {@code IN_PROGRESS}, {@code COMPLETED}) and never sent by the client.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Shift(Long id,
                    String zone,
                    Instant startTime,
                    Instant endTime,
                    int capacity,
                    long bookedCount,
                    long availableSlots,
                    String status,
                    Instant createdAt) {
}
