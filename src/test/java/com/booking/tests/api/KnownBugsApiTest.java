package com.booking.tests.api;

import com.booking.tests.api.model.ApiError;
import com.booking.tests.api.model.Booking;
import com.booking.tests.api.model.BookingRequest;
import com.booking.tests.api.model.Courier;
import com.booking.tests.api.model.CourierRequest;
import com.booking.tests.api.model.Shift;
import com.booking.tests.api.spec.Specs;
import com.booking.tests.data.CourierBuilder;
import io.qameta.allure.Allure;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.booking.tests.data.CourierBuilder.aCourier;
import static com.booking.tests.data.ShiftBuilder.aShift;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Known API bugs, one test each, documented in {@code documents/BUGS.md}.
 *
 * <p><b>Every test here is expected to FAIL while its bug exists.</b> Each asserts the correct behaviour: the
 * application README where it states a rule, otherwise the expectation we assumed and wrote down in BUGS.md.
 * A test turning green means the bug was fixed (or the test is wrong): never weaken an assertion to make one pass.
 * Excluded from the default run by the pom; replay with {@code mvn test -Dgroups=bugs -DexcludedGroups=}.
 */
@Tag("bugs")
@DisplayName("Known bugs (API): expected to fail")
class KnownBugsApiTest extends BaseApiTest {

    private static final long UNKNOWN_ID = Long.MAX_VALUE;

    @Test
    @DisplayName("BUG-02: a name of 1 character after trimming is rejected (README: 2–100 chars)")
    void rejectsNameShorterThanTwoCharactersAfterTrim() {
        // "  A  " is 5 characters as sent and passes @Size; the server then trims and stores "A".
        Response response = couriers.create(aCourier().name("  A  ").build());

        assertFieldError(response, "name");
    }

    @Test
    @DisplayName("BUG-03: a phone with 1 digit is rejected (README: 7–20 digits)")
    void rejectsPhoneWithOneDigit() {
        // The server regex counts dashes and spaces toward the length, so 1 digit + 6 dashes passes.
        Response response = couriers.create(aCourier().phone("1------").build());

        assertFieldError(response, "phone");
    }

    @Test
    @DisplayName("BUG-04: an unsupported Content-Type is answered with 415, not 500 (expectation assumed)")
    void answersUnsupportedContentTypeWith415() {
        // No client method: clients always send JSON. This edge case overrides the spec's content type.
        Response response = Allure.step("POST /bookings with Content-Type text/plain", () ->
                given().spec(Specs.request())
                        .contentType(ContentType.TEXT)
                        .body("courierId=1&shiftId=1")
                        .post("/bookings"));

        response.then().spec(Specs.status(415));
    }

    @Test
    @DisplayName("BUG-05: deactivating a courier does not leave them holding confirmed future bookings (expectation assumed)")
    void deactivationReleasesFutureBookings() {
        CourierBuilder courierData = aCourier();
        Courier courier = couriers.createOk(courierData.build());
        Shift shift = shifts.createOk(aShift().build());
        Booking booking = bookings.createOk(new BookingRequest(courier.id(), shift.id()));

        // Same builder, same email: only the active flag changes.
        couriers.update(courier.id(), courierData.inactive().build()).then().spec(Specs.ok());
        Booking after = bookings.get(booking.id()).then().spec(Specs.ok()).extract().as(Booking.class);

        // Neutral on the fix (cancel the bookings, or refuse to deactivate): an inactive courier cannot work them.
        assertNotEquals("CONFIRMED", after.status(), "inactive courier still holds a confirmed future booking");
    }

    @Test
    @DisplayName("BUG-08: an unknown courier is 404 on both booking endpoints (expectation assumed)")
    void unknownCourierIsNotFoundOnBothEndpoints() {
        int nested = couriers.bookings(UNKNOWN_ID).statusCode();
        int filtered = bookings.list(UNKNOWN_ID, null, null).statusCode();

        // The first holds today; the assertion pair documents the inconsistency in one place.
        assertAll(
                () -> assertEquals(404, nested, "GET /couriers/{unknown}/bookings"),
                () -> assertEquals(404, filtered, "GET /bookings?courierId={unknown}"));
    }

    @Test
    @DisplayName("BUG-09: createdAt returned on create equals createdAt read back (expectation assumed)")
    void createdAtIsStableBetweenCreateAndRead() {
        Courier courier = couriers.createOk(aCourier().build());
        Shift shift = shifts.createOk(aShift().build());
        Booking booking = bookings.createOk(new BookingRequest(courier.id(), shift.id()));

        Courier courierRead = couriers.get(courier.id()).then().spec(Specs.ok()).extract().as(Courier.class);
        Shift shiftRead = shifts.getOk(shift.id());
        Booking bookingRead = bookings.get(booking.id()).then().spec(Specs.ok()).extract().as(Booking.class);

        // Create responses carry nanoseconds from memory; reads carry what Postgres stored (microseconds, rounded).
        assertAll(
                () -> assertEquals(courier.createdAt(), courierRead.createdAt(), "courier createdAt"),
                () -> assertEquals(shift.createdAt(), shiftRead.createdAt(), "shift createdAt"),
                () -> assertEquals(booking.createdAt(), bookingRead.createdAt(), "booking createdAt"));
    }

    @Test
    @DisplayName("BUG-10: an invalid enum in the body is reported as a field error (expectation assumed)")
    void invalidVehicleTypeIsReportedAsFieldError() {
        CourierRequest valid = aCourier().build();
        CourierRequest invalid = new CourierRequest(valid.name(), valid.email(), valid.phone(), "HELICOPTER", valid.active());

        Response response = couriers.create(invalid);

        assertFieldError(response, "vehicleType");
    }

    /** 400 "Validation failed" whose {@code fieldErrors} names {@code field}. */
    private static void assertFieldError(Response response, String field) {
        ApiError error = response.then().spec(Specs.status(400)).extract().as(ApiError.class);
        assertAll(
                () -> assertEquals("Validation failed", error.message(), "message"),
                () -> assertNotNull(error.fieldErrors(), "fieldErrors"),
                () -> assertTrue(error.fieldErrors() != null && error.fieldErrors().containsKey(field),
                        "fieldErrors has " + field));
    }
}
