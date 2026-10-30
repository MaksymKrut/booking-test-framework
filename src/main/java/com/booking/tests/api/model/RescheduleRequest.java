package com.booking.tests.api.model;

/**
 * Body of {@code POST /bookings/{id}/reschedule}: the target shift. Boxed {@code Long} for the same reason as
 * {@link BookingRequest}: {@code null} is sent as an explicit JSON null, which produces the 400 "Validation failed".
 */
public record RescheduleRequest(Long shiftId) {
}
