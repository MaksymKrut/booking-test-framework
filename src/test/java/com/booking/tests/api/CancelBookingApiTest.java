package com.booking.tests.api;

import com.booking.tests.api.model.ApiError;
import com.booking.tests.api.model.Booking;
import com.booking.tests.api.model.BookingRequest;
import com.booking.tests.api.model.Courier;
import com.booking.tests.api.model.Shift;
import com.booking.tests.api.spec.Specs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.booking.tests.data.CourierBuilder.aCourier;
import static com.booking.tests.data.ShiftBuilder.aShift;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** {@code POST /bookings/{id}/cancel}. Cancelling is a state change, never a delete: the row stays. */
@DisplayName("POST /bookings/{id}/cancel")
class CancelBookingApiTest extends BaseApiTest {

    @Test
    @DisplayName("200 CANCELLED: same booking, cancelledAt set, contract holds")
    void cancelsConfirmedBooking() {
        Booking booking = bookNewShift(2);

        Booking cancelled = bookings.cancel(booking.id())
                .then()
                .spec(Specs.ok())
                .body(matchesJsonSchemaInClasspath(BOOKING_SCHEMA))
                .extract().as(Booking.class);

        assertAll(
                () -> assertEquals(booking.id(), cancelled.id(), "id"),
                () -> assertEquals("CANCELLED", cancelled.status(), "status"),
                () -> assertNotNull(cancelled.cancelledAt(), "cancelledAt"),
                () -> assertFalse(cancelled.cancelledAt().isBefore(booking.createdAt()), "cancelledAt >= createdAt"),
                () -> assertEquals(booking.courierId(), cancelled.courierId(), "courierId"),
                () -> assertEquals(booking.shiftId(), cancelled.shiftId(), "shiftId"));
    }

    @Test
    @DisplayName("409 on a second cancel of the same booking")
    void rejectsSecondCancel() {
        Booking booking = bookNewShift(2);
        bookings.cancelOk(booking.id());

        ApiError error = bookings.cancel(booking.id())
                .then()
                .spec(Specs.status(409))
                .extract().as(ApiError.class);

        assertEquals("Booking is already cancelled", error.message(), "message");
    }

    @Test
    @DisplayName("cancelling frees the slot: availableSlots back up by one")
    void cancellingFreesTheSlot() {
        Booking booking = bookNewShift(2);
        Shift whileBooked = shifts.getOk(booking.shiftId());

        bookings.cancelOk(booking.id());
        Shift afterCancel = shifts.getOk(booking.shiftId());

        // The "before" values are asserted too, so a test that never consumed the slot cannot pass.
        assertAll(
                () -> assertEquals(1, whileBooked.availableSlots(), "availableSlots while booked"),
                () -> assertEquals(2, afterCancel.availableSlots(), "availableSlots after cancel"),
                () -> assertEquals(0, afterCancel.bookedCount(), "bookedCount after cancel"));
    }

    /** Seeds a fresh courier and a fresh shift of the given capacity and books it. */
    private Booking bookNewShift(int capacity) {
        Courier courier = couriers.createOk(aCourier().build());
        Shift shift = shifts.createOk(aShift().capacity(capacity).build());
        return bookings.createOk(new BookingRequest(courier.id(), shift.id()));
    }
}
