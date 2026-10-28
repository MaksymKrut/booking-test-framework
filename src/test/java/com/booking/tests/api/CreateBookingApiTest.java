package com.booking.tests.api;

import com.booking.tests.api.model.ApiError;
import com.booking.tests.api.model.Booking;
import com.booking.tests.api.model.BookingRequest;
import com.booking.tests.api.model.Courier;
import com.booking.tests.api.model.Shift;
import com.booking.tests.api.spec.Specs;
import com.booking.tests.util.Waits;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static com.booking.tests.data.CourierBuilder.aCourier;
import static com.booking.tests.data.ShiftBuilder.aShift;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@code POST /bookings}. Every test seeds its own courier and shift through the API, so nothing depends on
 * seed rows, other tests, or execution order.
 */
@DisplayName("POST /bookings")
class CreateBookingApiTest extends BaseApiTest {

    /** An id no row will ever have: the API accepts any {@code Long}, sequences never get this far. */
    private static final long UNKNOWN_ID = Long.MAX_VALUE;

    @Test
    @DisplayName("201 CONFIRMED: contract, echoed courier/shift data, and the slot is taken")
    void createsConfirmedBooking() {
        Courier courier = couriers.createOk(aCourier().build());
        Shift shift = shifts.createOk(aShift().capacity(2).build());

        // Status, JSON content type and response time come from Specs.created(); the schema checks the shape.
        Booking booking = bookings.create(request(courier, shift))
                .then()
                .spec(Specs.created())
                .body(matchesJsonSchemaInClasspath(BOOKING_SCHEMA))
                .extract().as(Booking.class);

        // Side effect seen from the shift's side: one confirmed booking, one slot left.
        Shift after = shifts.getOk(shift.id());

        assertAll(
                () -> assertNotNull(booking.id(), "id"),
                () -> assertEquals("CONFIRMED", booking.status(), "status"),
                () -> assertEquals(courier.id(), booking.courierId(), "courierId"),
                () -> assertEquals(courier.name(), booking.courierName(), "courierName"),
                () -> assertEquals(shift.id(), booking.shiftId(), "shiftId"),
                () -> assertEquals(shift.zone(), booking.zone(), "zone"),
                () -> assertEquals(shift.startTime(), booking.shiftStart(), "shiftStart"),
                () -> assertEquals(shift.endTime(), booking.shiftEnd(), "shiftEnd"),
                () -> assertNotNull(booking.createdAt(), "createdAt"),
                () -> assertNull(booking.cancelledAt(), "cancelledAt"),
                () -> assertEquals(1, after.bookedCount(), "shift bookedCount"),
                () -> assertEquals(1, after.availableSlots(), "shift availableSlots"));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Rule.class)
    @DisplayName("rejects a booking that breaks a rule (server precedence order)")
    void rejectsBookingThatBreaksRule(Rule rule) {
        Arranged arranged = rule.arrange(this);

        ApiError error = bookings.create(arranged.request())
                .then()
                .spec(Specs.status(rule.status))
                .extract().as(ApiError.class);

        // fieldErrors must be absent for business rules and carry exactly the null fields for validation.
        Set<String> fieldErrorKeys = error.fieldErrors() == null ? Set.of() : error.fieldErrors().keySet();
        assertAll(
                () -> assertEquals(rule.status, error.status(), "status in body"),
                () -> assertEquals(arranged.message(), error.message(), "message"),
                () -> assertEquals("/api/bookings", error.path(), "path"),
                () -> assertEquals(arranged.fieldErrors(), fieldErrorKeys, "fieldErrors keys"));
    }

    @Test
    @DisplayName("201 for a back-to-back shift: end == next start is not an overlap")
    void allowsBackToBackShift() {
        Courier courier = couriers.createOk(aCourier().build());
        Shift first = shifts.createOk(aShift().build());
        bookings.createOk(request(courier, first));
        Shift next = shifts.createOk(aShift().backToBack(first).build());

        Booking booking = bookings.create(request(courier, next))
                .then().spec(Specs.created())
                .extract().as(Booking.class);

        assertEquals("CONFIRMED", booking.status(), "status");
    }

    @Test
    @DisplayName("201 when re-booking a shift after cancelling: only CONFIRMED bookings block")
    void allowsRebookingAfterCancel() {
        Courier courier = couriers.createOk(aCourier().build());
        Shift shift = shifts.createOk(aShift().build());
        Booking cancelled = bookings.createOk(request(courier, shift));
        bookings.cancelOk(cancelled.id());

        Booking rebooked = bookings.create(request(courier, shift))
                .then().spec(Specs.created())
                .extract().as(Booking.class);

        assertAll(
                () -> assertEquals("CONFIRMED", rebooked.status(), "status"),
                () -> assertNotEquals(cancelled.id(), rebooked.id(), "a new booking row, not the old one revived"));
    }

    private static BookingRequest request(Courier courier, Shift shift) {
        return new BookingRequest(courier.id(), shift.id());
    }

    /** What a {@link Rule} set up: the request to send and the error the server must answer with. */
    record Arranged(BookingRequest request, String message, Set<String> fieldErrors) {

        Arranged(BookingRequest request, String message) {
            this(request, message, Set.of());
        }
    }

    /**
     * One constant per server rule, declared in the order the server checks them. Each constant seeds only
     * the data needed to break <em>its own</em> rule, with everything else valid, so a failure points at
     * exactly one rule. Messages are the server's literal texts.
     */
    enum Rule {
        COURIER_NOT_FOUND(404) {
            @Override
            Arranged arrange(CreateBookingApiTest t) {
                Shift shift = t.shifts.createOk(aShift().build());
                return new Arranged(new BookingRequest(UNKNOWN_ID, shift.id()),
                        "Courier with id " + UNKNOWN_ID + " not found");
            }
        },
        SHIFT_NOT_FOUND(404) {
            @Override
            Arranged arrange(CreateBookingApiTest t) {
                Courier courier = t.couriers.createOk(aCourier().build());
                return new Arranged(new BookingRequest(courier.id(), UNKNOWN_ID),
                        "Shift with id " + UNKNOWN_ID + " not found");
            }
        },
        COURIER_INACTIVE(409) {
            @Override
            Arranged arrange(CreateBookingApiTest t) {
                Courier courier = t.couriers.createOk(aCourier().inactive().build());
                Shift shift = t.shifts.createOk(aShift().build());
                return new Arranged(request(courier, shift), "Courier is inactive and cannot book shifts");
            }
        },
        SHIFT_STARTED(409) {
            @Override
            Arranged arrange(CreateBookingApiTest t) {
                // A past shift cannot be created, so create one 3 s ahead and wait until the server
                // itself reports it IN_PROGRESS (server status is the source of truth, not the local clock).
                Courier courier = t.couriers.createOk(aCourier().build());
                Shift shift = t.shifts.createOk(aShift().startingIn(Duration.ofSeconds(3)).build());
                Waits.until(() -> "IN_PROGRESS".equals(t.shifts.getOk(shift.id()).status()),
                        Duration.ofSeconds(15), Duration.ofMillis(500), "shift " + shift.id() + " is IN_PROGRESS");
                return new Arranged(request(courier, shift), "Shift has already started and can no longer be booked");
            }
        },
        DUPLICATE_BOOKING(409) {
            @Override
            Arranged arrange(CreateBookingApiTest t) {
                Courier courier = t.couriers.createOk(aCourier().build());
                Shift shift = t.shifts.createOk(aShift().build());
                t.bookings.createOk(request(courier, shift));
                return new Arranged(request(courier, shift), "Courier has already booked this shift");
            }
        },
        OVERLAPPING_SHIFT(409) {
            @Override
            Arranged arrange(CreateBookingApiTest t) {
                Courier courier = t.couriers.createOk(aCourier().build());
                Shift booked = t.shifts.createOk(aShift().build());
                t.bookings.createOk(request(courier, booked));
                Shift overlapping = t.shifts.createOk(aShift().overlapping(booked).build());
                return new Arranged(request(courier, overlapping),
                        "Courier already has a booking that overlaps with this shift");
            }
        },
        SHIFT_FULL(409) {
            @Override
            Arranged arrange(CreateBookingApiTest t) {
                Shift shift = t.shifts.createOk(aShift().capacity(1).build());
                t.bookings.createOk(request(t.couriers.createOk(aCourier().build()), shift));
                Courier latecomer = t.couriers.createOk(aCourier().build());
                return new Arranged(request(latecomer, shift), "Shift is full");
            }
        },
        NULL_FIELDS(400) {
            @Override
            Arranged arrange(CreateBookingApiTest t) {
                // Explicit JSON nulls (the mapper keeps nulls); fieldErrors must name both fields.
                return new Arranged(new BookingRequest(null, null), "Validation failed", Set.of("courierId", "shiftId"));
            }
        };

        final int status;

        Rule(int status) {
            this.status = status;
        }

        abstract Arranged arrange(CreateBookingApiTest t);
    }
}
