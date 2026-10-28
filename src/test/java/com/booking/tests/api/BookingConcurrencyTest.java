package com.booking.tests.api;

import com.booking.tests.api.model.Booking;
import com.booking.tests.api.model.BookingRequest;
import com.booking.tests.api.model.Courier;
import com.booking.tests.api.model.Shift;
import com.booking.tests.api.spec.Specs;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
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
 * {@code POST /bookings} under real contention. Data is seeded sequentially, then all booking requests are
 * released at the same instant from their own threads (see {@link #sendAtOnce}).
 *
 * <p>Concurrent responses are checked by status code and message only, not with {@code Specs}: the 2 s
 * response-time ceiling is a per-call SLA and not what these tests measure under deliberate contention.
 */
@DisplayName("POST /bookings under concurrency")
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

    /**
     * Sends each request from its own thread and returns the responses in request order.
     * Two latches make the start simultaneous: every thread counts down {@code ready} and parks on {@code go};
     * only when all are parked does the test open {@code go}, so no request gets a head start from thread
     * creation. The pool is closed (and awaited) by try-with-resources.
     */
    private List<Response> sendAtOnce(List<BookingRequest> requests) throws Exception {
        CountDownLatch ready = new CountDownLatch(requests.size());
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(requests.size())) {
            List<Future<Response>> futures = requests.stream()
                    .map(request -> pool.submit(() -> {
                        ready.countDown();
                        go.await();
                        return bookings.create(request);
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
