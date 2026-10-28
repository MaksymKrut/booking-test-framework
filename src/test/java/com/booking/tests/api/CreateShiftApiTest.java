package com.booking.tests.api;

import com.booking.tests.api.model.ApiError;
import com.booking.tests.api.model.Shift;
import com.booking.tests.api.model.ShiftRequest;
import com.booking.tests.api.spec.Specs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static com.booking.tests.data.ShiftBuilder.aShift;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code POST /shifts} boundary values. Server rules: start in the future, end after start,
 * duration 1–12 h inclusive, capacity 1–50. Each limit is tested on both sides.
 * Time rules answer with a plain message; capacity is bean validation and answers with {@code fieldErrors}.
 */
@DisplayName("POST /shifts")
class CreateShiftApiTest extends BaseApiTest {

    private static final String DURATION_RULE = "Shift duration must be between 1 and 12 hours";

    @ParameterizedTest(name = "{0} min, capacity {1} → 201")
    @CsvSource({
            "60,  2",   // shortest legal duration (1 h)
            "720, 2",   // longest legal duration (12 h)
            "240, 1",   // smallest capacity
            "240, 50"   // largest capacity
    })
    @DisplayName("accepts values on the inner side of every limit")
    void acceptsBoundaryValues(long minutes, int capacity) {
        ShiftRequest request = aShift().duration(Duration.ofMinutes(minutes)).capacity(capacity).build();

        Shift shift = shifts.create(request)
                .then().spec(Specs.created())
                .extract().as(Shift.class);

        assertAll(
                () -> assertNotNull(shift.id(), "id"),
                () -> assertEquals(request.zone(), shift.zone(), "zone"),
                () -> assertEquals(request.startTime(), shift.startTime(), "startTime"),
                () -> assertEquals(request.endTime(), shift.endTime(), "endTime"),
                () -> assertEquals(capacity, shift.capacity(), "capacity"),
                () -> assertEquals(0, shift.bookedCount(), "bookedCount"),
                () -> assertEquals(capacity, shift.availableSlots(), "availableSlots"),
                () -> assertEquals("OPEN", shift.status(), "status"));
    }

    @ParameterizedTest(name = "{0} min → 400 \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "59  | " + DURATION_RULE,                      // 1 minute short of 1 h
            "721 | " + DURATION_RULE,                      // 1 minute over 12 h
            "0   | Shift end time must be after start time" // end == start
    })
    @DisplayName("rejects durations on the outer side of the limits")
    void rejectsInvalidDuration(long minutes, String message) {
        ShiftRequest request = aShift().duration(Duration.ofMinutes(minutes)).build();

        ApiError error = shifts.create(request)
                .then().spec(Specs.status(400))
                .extract().as(ApiError.class);

        assertEquals(message, error.message(), "message");
    }

    @ParameterizedTest(name = "capacity {0} → 400 fieldErrors.capacity")
    @ValueSource(ints = {0, 51})
    @DisplayName("rejects capacity outside 1–50")
    void rejectsCapacityOutOfRange(int capacity) {
        ApiError error = shifts.create(aShift().capacity(capacity).build())
                .then().spec(Specs.status(400))
                .extract().as(ApiError.class);

        // Assert the key, not the text: the constraint message is the server's wording, not the contract.
        assertAll(
                () -> assertEquals("Validation failed", error.message(), "message"),
                () -> assertNotNull(error.fieldErrors(), "fieldErrors"),
                () -> assertTrue(error.fieldErrors().containsKey("capacity"), "fieldErrors has capacity"));
    }

    @Test
    @DisplayName("rejects a start time in the past")
    void rejectsStartInThePast() {
        ApiError error = shifts.create(aShift().inThePast().build())
                .then().spec(Specs.status(400))
                .extract().as(ApiError.class);

        assertEquals("Shift start time must be in the future", error.message(), "message");
    }
}
