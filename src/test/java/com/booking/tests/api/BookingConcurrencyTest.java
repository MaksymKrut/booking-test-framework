package com.booking.tests.api;

import com.booking.tests.api.model.Booking;
import com.booking.tests.api.model.BookingRequest;
import com.booking.tests.api.model.Courier;
import com.booking.tests.api.model.RescheduleRequest;
import com.booking.tests.api.model.Shift;
import com.booking.tests.api.spec.Specs;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.booking.tests.data.CourierBuilder.aCourier;
import static com.booking.tests.data.ShiftBuilder.aShift;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code POST /bookings} and {@code POST /bookings/{id}/reschedule} under real contention. Data is seeded
 * sequentially, then all requests are released at the same instant from their own threads (see {@link #sendAllAtOnce}).
 *
 * <p>Concurrent responses are checked by status code and message only, not with {@code Specs}: the 2 s
 * response-time ceiling is a per-call SLA and not what these tests measure under deliberate contention.
 */
@DisplayName("Bookings under concurrency")
class BookingConcurrencyTest extends BaseApiTest {

    private static final int COURIERS = 10;
    private static final int OVERLAPPING_SHIFTS = 5;

    @Test
    @DisplayName("capacity-1 shift, 10 couriers at once: exactly one 201, nine 409 \"Shift is full\"")
    void capacityHoldsUnderParallelBookings() throws Exception {
        Shift shift = shifts.createOk(aShift().capacity(1).build());
        List<BookingRequest> requests = IntStream.range(0, COURIERS)
                .mapToObj(i -> new BookingRequest(couriers.createOk(aCourier().build()).id(), shift.id()))
                .toList();

        List<Response> responses = sendAtOnce(requests);
        Shift after = shifts.getOk(shift.id());

        assertAll(
                () -> assertEquals(1, count(responses, 201), "201 responses"),
                () -> assertEquals(COURIERS - 1, count(responses, 409), "409 responses"),
                () -> assertEquals(Set.of("Shift is full"), messages(responses, 409), "409 messages"),
                () -> assertEquals(1, after.bookedCount(), "shift bookedCount"),
                () -> assertEquals(0, after.availableSlots(), "shift availableSlots"),
                () -> assertEquals("FULL", after.status(), "shift status"));
    }

    /**
     * Expected to FAIL while BUG-01 exists: the server serialises capacity with a row lock on the shift, but the
     * overlap check is a plain read per courier, so parallel requests for different shifts all pass it.
     * Five identical-time shifts (instead of two) make a real collision far more likely; a lucky run can still
     * pass, which is itself a sign of a race.
     */
    @Test
    @Tag("bugs")
    @DisplayName("BUG-01: one courier, 5 overlapping shifts at once: only one booking may succeed")
    void overlapCheckHoldsUnderParallelBookings() throws Exception {
        Courier courier = couriers.createOk(aCourier().build());
        Shift first = shifts.createOk(aShift().build());
        List<BookingRequest> requests = Stream.concat(
                        Stream.of(first),
                        IntStream.range(1, OVERLAPPING_SHIFTS)
                                .mapToObj(i -> shifts.createOk(aShift().startingAt(first.startTime()).build())))
                .map(shift -> new BookingRequest(courier.id(), shift.id()))
                .toList();

        List<Response> responses = sendAtOnce(requests);
        long confirmed = Arrays.stream(couriers.bookings(courier.id())
                        .then().spec(Specs.ok())
                        .extract().as(Booking[].class))
                .filter(booking -> "CONFIRMED".equals(booking.status()))
                .count();

        assertAll(
                () -> assertEquals(1, count(responses, 201), "201 responses"),
                () -> assertEquals(OVERLAPPING_SHIFTS - 1, count(responses, 409), "409 responses"),
                () -> assertEquals(1, confirmed, "CONFIRMED bookings of the courier"));
    }

    @Test
    @DisplayName("reschedule: 10 bookings into one capacity-1 shift at once: exactly one 200, nine 409 \"Shift is full\"")
    void capacityHoldsUnderParallelReschedules() throws Exception {
        Shift target = shifts.createOk(aShift().capacity(1).build());
        // One courier per booking, each on its own default shift: no overlap rule can interfere, only capacity.
        List<Booking> booked = IntStream.range(0, COURIERS)
                .mapToObj(i -> bookings.createOk(new BookingRequest(
                        couriers.createOk(aCourier().build()).id(), shifts.createOk(aShift().build()).id())))
                .toList();

        List<Response> responses = sendAllAtOnce(booked.stream()
                .map(booking -> (Callable<Response>) () ->
                        bookings.reschedule(booking.id(), new RescheduleRequest(target.id())))
                .toList());
        Shift after = shifts.getOk(target.id());

        // Losers must be untouched: still on the shift they were booked on, not half-moved.
        List<Executable> losersUntouched = IntStream.range(0, COURIERS)
                .filter(i -> responses.get(i).statusCode() == 409)
                .mapToObj(i -> (Executable) () -> assertEquals(booked.get(i).shiftId(),
                        bookings.getOk(booked.get(i).id()).shiftId(), "booking " + booked.get(i).id() + " shiftId"))
                .toList();
        assertAll(
                () -> assertEquals(1, count(responses, 200), "200 responses"),
                () -> assertEquals(COURIERS - 1, count(responses, 409), "409 responses"),
                () -> assertEquals(Set.of("Shift is full"), messages(responses, 409), "409 messages"),
                () -> assertEquals(1, after.bookedCount(), "target bookedCount"),
                () -> assertEquals("FULL", after.status(), "target status"),
                () -> assertAll("losing bookings stay on their shift", losersUntouched));
    }

    /**
     * The two requests serialise on the booking's row lock, and each move is valid when it is applied (the targets
     * do not overlap), so both succeed in some order. What must hold is the end state: one booking on exactly one
     * of the targets, the original slot freed, and the shift counts agreeing with where the booking actually is.
     */
    @Test
    @DisplayName("reschedule: same booking to two shifts at once: both 200, booking on one target, counts consistent")
    void sameBookingRescheduledTwiceEndsConsistent() throws Exception {
        Courier courier = couriers.createOk(aCourier().build());
        Shift origin = shifts.createOk(aShift().build());
        Shift first = shifts.createOk(aShift().build());
        Shift second = shifts.createOk(aShift().build());
        Booking booking = bookings.createOk(new BookingRequest(courier.id(), origin.id()));

        List<Response> responses = sendAllAtOnce(Stream.of(first, second)
                .map(target -> (Callable<Response>) () ->
                        bookings.reschedule(booking.id(), new RescheduleRequest(target.id())))
                .toList());

        Booking after = bookings.getOk(booking.id());
        long originBooked = shifts.getOk(origin.id()).bookedCount();
        long firstBooked = shifts.getOk(first.id()).bookedCount();
        long secondBooked = shifts.getOk(second.id()).bookedCount();
        assertAll(
                () -> assertEquals(2, count(responses, 200), "200 responses"),
                () -> assertTrue(Set.of(first.id(), second.id()).contains(after.shiftId()),
                        "booking is on one of the targets, was on " + after.shiftId()),
                () -> assertEquals(0, originBooked, "origin bookedCount"),
                () -> assertEquals(1, firstBooked + secondBooked, "bookedCount over both targets"),
                () -> assertEquals(after.shiftId().equals(first.id()) ? 1 : 0, firstBooked,
                        "first target bookedCount matches where the booking is"));
    }

    /**
     * Expected to FAIL while BUG-12 exists: same cause as BUG-01. The reschedule locks the booking and the target
     * shift, never the courier, so moves of <em>different</em> bookings of one courier hold different locks and
     * all pass the overlap check before any of them commits. Five bookings on separate days are moved at once onto
     * five shifts with identical times; only one move may succeed.
     */
    @Test
    @Tag("bugs")
    @DisplayName("BUG-12: one courier, 5 bookings rescheduled at once onto overlapping shifts: only one may succeed")
    void overlapCheckHoldsUnderParallelReschedules() throws Exception {
        Courier courier = couriers.createOk(aCourier().build());
        List<Booking> booked = IntStream.range(0, OVERLAPPING_SHIFTS)
                .mapToObj(i -> bookings.createOk(new BookingRequest(courier.id(), shifts.createOk(aShift().build()).id())))
                .toList();
        Shift firstTarget = shifts.createOk(aShift().build());
        List<Shift> targets = Stream.concat(
                        Stream.of(firstTarget),
                        IntStream.range(1, OVERLAPPING_SHIFTS)
                                .mapToObj(i -> shifts.createOk(aShift().startingAt(firstTarget.startTime()).build())))
                .toList();

        List<Response> responses = sendAllAtOnce(IntStream.range(0, OVERLAPPING_SHIFTS)
                .mapToObj(i -> (Callable<Response>) () ->
                        bookings.reschedule(booked.get(i).id(), new RescheduleRequest(targets.get(i).id())))
                .toList());
        Set<Long> targetIds = targets.stream().map(Shift::id).collect(Collectors.toSet());
        long onTargets = Arrays.stream(couriers.bookings(courier.id())
                        .then().spec(Specs.ok())
                        .extract().as(Booking[].class))
                .filter(b -> "CONFIRMED".equals(b.status()) && targetIds.contains(b.shiftId()))
                .count();

        assertAll(
                () -> assertEquals(1, count(responses, 200), "200 responses"),
                () -> assertEquals(OVERLAPPING_SHIFTS - 1, count(responses, 409), "409 responses"),
                () -> assertEquals(1, onTargets, "CONFIRMED bookings of the courier on the overlapping targets"));
    }

    /** {@link #sendAllAtOnce} for {@code POST /bookings}. */
    private List<Response> sendAtOnce(List<BookingRequest> requests) throws Exception {
        return sendAllAtOnce(requests.stream()
                .map(request -> (Callable<Response>) () -> bookings.create(request))
                .toList());
    }

    /**
     * Runs each call on its own thread and returns the responses in call order.
     * Two latches make the start simultaneous: every thread counts down {@code ready} and parks on {@code go};
     * only when all are parked does the test open {@code go}, so no request gets a head start from thread
     * creation. The pool is closed (and awaited) by try-with-resources.
     */
    private List<Response> sendAllAtOnce(List<Callable<Response>> calls) throws Exception {
        CountDownLatch ready = new CountDownLatch(calls.size());
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(calls.size())) {
            List<Future<Response>> futures = calls.stream()
                    .map(call -> pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        return call.call();
                    }))
                    .toList();
            assertTrue(ready.await(10, TimeUnit.SECONDS), "all threads parked at the start gate");
            go.countDown();

            List<Response> responses = new ArrayList<>();
            for (Future<Response> future : futures) {
                responses.add(future.get(30, TimeUnit.SECONDS));
            }
            return responses;
        }
    }

    private static long count(List<Response> responses, int status) {
        return responses.stream().filter(response -> response.statusCode() == status).count();
    }

    private static Set<String> messages(List<Response> responses, int status) {
        return responses.stream()
                .filter(response -> response.statusCode() == status)
                .map(response -> response.jsonPath().getString("message"))
                .collect(Collectors.toSet());
    }
}
