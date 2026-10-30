package com.booking.tests.api;

import com.booking.tests.api.model.ApiError;
import com.booking.tests.api.model.Booking;
import com.booking.tests.api.model.BookingRequest;
import com.booking.tests.api.model.Courier;
import com.booking.tests.api.model.RescheduleRequest;
import com.booking.tests.api.model.Shift;
import com.booking.tests.api.spec.Specs;
import com.booking.tests.data.CourierBuilder;
import com.booking.tests.util.Waits;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.util.Set;

import static com.booking.tests.data.CourierBuilder.aCourier;
import static com.booking.tests.data.ShiftBuilder.aShift;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@code POST /bookings/{id}/reschedule} (AUT PR #1, commit {@code 8a5ab94}). Expected behaviour is the contract in
 * the PR description and the application README: the <em>same</em> booking moves to the target shift, keeps its id
 * and {@code createdAt}, frees the old slot and takes the new one; no new booking row is created.
 *
 * <p>Known defects of this endpoint are not asserted here but in {@code KnownBugsApiTest} (BUG-11, BUG-13) and
 * {@code BookingConcurrencyTest} (BUG-12), so this class stays green while they exist.
 */
@DisplayName("POST /bookings/{id}/reschedule")
class RescheduleBookingApiTest extends BaseApiTest {

    /** An id no row will ever have: the API accepts any {@code Long}, sequences never get this far. */
    private static final long UNKNOWN_ID = Long.MAX_VALUE;

    @Test
    @DisplayName("200: same booking moved to the target, createdAt kept, old slot freed, new slot taken, no new row")
    void movesBookingToTargetShift() {
        Courier courier = couriers.createOk(aCourier().build());
        Shift current = shifts.createOk(aShift().capacity(2).build());
        Shift target = shifts.createOk(aShift().capacity(2).build());
        Booking booked = bookings.createOk(new BookingRequest(courier.id(), current.id()));
        // Baseline from a read, not from the create response: create responses carry nanoseconds that the database
        // does not store (BUG-09), so comparing with them would fail for a reason unrelated to rescheduling.
        Booking before = bookings.getOk(booked.id());

        Booking moved = bookings.reschedule(booked.id(), new RescheduleRequest(target.id()))
                .then()
                .spec(Specs.ok())
                .body(matchesJsonSchemaInClasspath(BOOKING_SCHEMA))
                .extract().as(Booking.class);

        Shift currentAfter = shifts.getOk(current.id());
        Shift targetAfter = shifts.getOk(target.id());
        Booking[] courierBookings = couriers.bookings(courier.id())
                .then().spec(Specs.ok())
                .extract().as(Booking[].class);

        assertAll(
                () -> assertEquals(booked.id(), moved.id(), "id (same booking, not a new one)"),
                () -> assertEquals("CONFIRMED", moved.status(), "status"),
                () -> assertEquals(courier.id(), moved.courierId(), "courierId"),
                () -> assertEquals(courier.name(), moved.courierName(), "courierName"),
                () -> assertEquals(target.id(), moved.shiftId(), "shiftId"),
                () -> assertEquals(target.zone(), moved.zone(), "zone"),
                () -> assertEquals(target.startTime(), moved.shiftStart(), "shiftStart"),
                () -> assertEquals(target.endTime(), moved.shiftEnd(), "shiftEnd"),
                () -> assertEquals(before.createdAt(), moved.createdAt(), "createdAt unchanged"),
                () -> assertNull(moved.cancelledAt(), "cancelledAt"),
                () -> assertEquals(0, currentAfter.bookedCount(), "old shift bookedCount"),
                () -> assertEquals(2, currentAfter.availableSlots(), "old shift availableSlots"),
                () -> assertEquals(1, targetAfter.bookedCount(), "target bookedCount"),
                () -> assertEquals(1, targetAfter.availableSlots(), "target availableSlots"),
                () -> assertEquals(1, courierBookings.length, "bookings of the courier (no new row)"));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Rule.class)
    @DisplayName("rejects a reschedule that breaks a rule (PR contract order)")
    void rejectsRescheduleThatBreaksRule(Rule rule) {
        Arranged arranged = rule.arrange(this);

        ApiError error = bookings.reschedule(arranged.bookingId(), arranged.request())
                .then()
                .spec(Specs.status(rule.status))
                .extract().as(ApiError.class);

        // fieldErrors must be absent for business rules and carry exactly the null field for validation.
        Set<String> fieldErrorKeys = error.fieldErrors() == null ? Set.of() : error.fieldErrors().keySet();
        assertAll(
                () -> assertEquals(rule.status, error.status(), "status in body"),
                () -> assertEquals(arranged.message(), error.message(), "message"),
                () -> assertEquals("/api/bookings/" + arranged.bookingId() + "/reschedule", error.path(), "path"),
                () -> assertEquals(arranged.fieldErrors(), fieldErrorKeys, "fieldErrors keys"));
    }

    @Test
    @DisplayName("200 for a target back-to-back with another booking: end == next start is not an overlap")
    void allowsTargetBackToBackWithOtherBooking() {
        Courier courier = couriers.createOk(aCourier().build());
        Booking moving = bookings.createOk(new BookingRequest(courier.id(), shifts.createOk(aShift().build()).id()));
        Shift other = shifts.createOk(aShift().build());
        bookings.createOk(new BookingRequest(courier.id(), other.id()));
        // The target touches the courier's *other* booking, never the one being moved, so BUG-11 cannot interfere.
        Shift target = shifts.createOk(aShift().backToBack(other).build());

        Booking moved = bookings.reschedule(moving.id(), new RescheduleRequest(target.id()))
                .then().spec(Specs.ok())
                .extract().as(Booking.class);

        assertEquals(target.id(), moved.shiftId(), "shiftId");
    }

    @Test
    @DisplayName("200 when moving back to the original shift: the freed slot can be taken again")
    void allowsMovingBackToOriginalShift() {
        Courier courier = couriers.createOk(aCourier().build());
        // Capacity 1: if the first move did not free the slot, the way back would be 409 "Shift is full".
        Shift original = shifts.createOk(aShift().capacity(1).build());
        Shift detour = shifts.createOk(aShift().build());
        Booking booking = bookings.createOk(new BookingRequest(courier.id(), original.id()));
        bookings.rescheduleOk(booking.id(), detour.id());

        Booking back = bookings.reschedule(booking.id(), new RescheduleRequest(original.id()))
                .then().spec(Specs.ok())
                .extract().as(Booking.class);

        Shift originalAfter = shifts.getOk(original.id());
        Shift detourAfter = shifts.getOk(detour.id());
        assertAll(
                () -> assertEquals(original.id(), back.shiftId(), "shiftId"),
                () -> assertEquals(1, originalAfter.bookedCount(), "original bookedCount"),
                () -> assertEquals("FULL", originalAfter.status(), "original status"),
                () -> assertEquals(0, detourAfter.bookedCount(), "detour bookedCount"));
    }

    /** Seeds a fresh shift (default capacity 2) and books it for {@code courier}. */
    private Booking bookFreshShift(Courier courier) {
        return bookings.createOk(new BookingRequest(courier.id(), shifts.createOk(aShift().build()).id()));
    }

    /** What a {@link Rule} set up: the booking to move, the body to send and the error the server must answer with. */
    record Arranged(long bookingId, RescheduleRequest request, String message, Set<String> fieldErrors) {

        Arranged(long bookingId, RescheduleRequest request, String message) {
            this(bookingId, request, message, Set.of());
        }
    }

    /**
     * One constant per rule of the PR's error table, in the order the server checks them. Each constant seeds only
     * what is needed to break <em>its own</em> rule, with everything else valid, so a failure points at exactly one
     * rule. Messages are the server's literal texts as stated in the PR.
     */
    enum Rule {
        BOOKING_NOT_FOUND(404) {
            @Override
            Arranged arrange(RescheduleBookingApiTest t) {
                Shift target = t.shifts.createOk(aShift().build());
                return new Arranged(UNKNOWN_ID, new RescheduleRequest(target.id()),
                        "Booking with id " + UNKNOWN_ID + " not found");
            }
        },
        SHIFT_NOT_FOUND(404) {
            @Override
            Arranged arrange(RescheduleBookingApiTest t) {
                Booking booking = t.bookFreshShift(t.couriers.createOk(aCourier().build()));
                return new Arranged(booking.id(), new RescheduleRequest(UNKNOWN_ID),
                        "Shift with id " + UNKNOWN_ID + " not found");
            }
        },
        BOOKING_CANCELLED(409) {
            @Override
            Arranged arrange(RescheduleBookingApiTest t) {
                Booking booking = t.bookFreshShift(t.couriers.createOk(aCourier().build()));
                t.bookings.cancelOk(booking.id());
                Shift target = t.shifts.createOk(aShift().build());
                return new Arranged(booking.id(), new RescheduleRequest(target.id()),
                        "Booking is cancelled and cannot be rescheduled");
            }
        },
        SAME_SHIFT(409) {
            @Override
            Arranged arrange(RescheduleBookingApiTest t) {
                Booking booking = t.bookFreshShift(t.couriers.createOk(aCourier().build()));
                return new Arranged(booking.id(), new RescheduleRequest(booking.shiftId()),
                        "Booking is already on this shift");
            }
        },
        COURIER_INACTIVE(409) {
            @Override
            Arranged arrange(RescheduleBookingApiTest t) {
                // Book while active, then deactivate: an inactive courier cannot book, so this is the only order.
                // Same builder, same email: only the active flag changes.
                CourierBuilder courierData = aCourier();
                Courier courier = t.couriers.createOk(courierData.build());
                Booking booking = t.bookFreshShift(courier);
                t.couriers.update(courier.id(), courierData.inactive().build()).then().spec(Specs.ok());
                Shift target = t.shifts.createOk(aShift().build());
                return new Arranged(booking.id(), new RescheduleRequest(target.id()),
                        "Courier is inactive and cannot book shifts");
            }
        },
        TARGET_STARTED(409) {
            @Override
            Arranged arrange(RescheduleBookingApiTest t) {
                // A past shift cannot be created, so create the target 3 s ahead and wait until the server
                // itself reports it IN_PROGRESS (server status is the source of truth, not the local clock).
                Booking booking = t.bookFreshShift(t.couriers.createOk(aCourier().build()));
                Shift target = t.shifts.createOk(aShift().startingIn(Duration.ofSeconds(3)).build());
                Waits.until(() -> "IN_PROGRESS".equals(t.shifts.getOk(target.id()).status()),
                        Duration.ofSeconds(15), Duration.ofMillis(500), "shift " + target.id() + " is IN_PROGRESS");
                return new Arranged(booking.id(), new RescheduleRequest(target.id()),
                        "Shift has already started and can no longer be booked");
            }
        },
        OVERLAP_WITH_OTHER_BOOKING(409) {
            @Override
            Arranged arrange(RescheduleBookingApiTest t) {
                // The target overlaps the courier's *other* booking only (default shifts never overlap each other),
                // so this is the rule as specified, not the self-overlap of BUG-11.
                Courier courier = t.couriers.createOk(aCourier().build());
                Booking moving = t.bookFreshShift(courier);
                Shift other = t.shifts.createOk(aShift().build());
                t.bookings.createOk(new BookingRequest(courier.id(), other.id()));
                Shift target = t.shifts.createOk(aShift().overlapping(other).build());
                return new Arranged(moving.id(), new RescheduleRequest(target.id()),
                        "Courier already has a booking that overlaps with this shift");
            }
        },
        TARGET_FULL(409) {
            @Override
            Arranged arrange(RescheduleBookingApiTest t) {
                Booking booking = t.bookFreshShift(t.couriers.createOk(aCourier().build()));
                Shift target = t.shifts.createOk(aShift().capacity(1).build());
                t.bookings.createOk(new BookingRequest(t.couriers.createOk(aCourier().build()).id(), target.id()));
                return new Arranged(booking.id(), new RescheduleRequest(target.id()), "Shift is full");
            }
        },
        NULL_SHIFT_ID(400) {
            @Override
            Arranged arrange(RescheduleBookingApiTest t) {
                // A real booking, so only the body is wrong; explicit JSON null (the mapper keeps nulls).
                Booking booking = t.bookFreshShift(t.couriers.createOk(aCourier().build()));
                return new Arranged(booking.id(), new RescheduleRequest(null), "Validation failed", Set.of("shiftId"));
            }
        };

        final int status;

        Rule(int status) {
            this.status = status;
        }

        abstract Arranged arrange(RescheduleBookingApiTest t);
    }
}
