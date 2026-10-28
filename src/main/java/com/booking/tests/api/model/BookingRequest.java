package com.booking.tests.api.model;

/**
 * Body of {@code POST /bookings}. Boxed {@code Long} on purpose: {@code null} is serialized as an explicit
 * JSON null (see {@code Json}), which is how the 400 "Validation failed" case is produced.
 */
public record BookingRequest(Long courierId, Long shiftId) {
}
