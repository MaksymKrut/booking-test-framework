package com.booking.tests.api;

import com.booking.tests.api.model.VehicleType;
import com.booking.tests.api.model.Zone;
import com.booking.tests.api.spec.Specs;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Smoke: the API is up and its reference data matches the framework's enums. If the server adds or
 * renames a zone or vehicle type, this test fails first and points at the enums to update.
 * {@code /meta} has no client: it is a single read-only call used only here.
 */
@Tag("smoke")
@DisplayName("GET /meta")
class MetaApiTest extends BaseApiTest {

    private static final Set<String> ZONES = names(Zone.values());
    private static final Set<String> VEHICLE_TYPES = names(VehicleType.values());

    @Test
    @DisplayName("returns all zones and vehicle types within the response-time ceiling")
    void returnsReferenceData() {
        JsonPath body = given().spec(Specs.request())
                .when().get("/meta")
                .then().spec(Specs.ok())
                .extract().jsonPath();

        List<String> zones = body.getList("zones", String.class);
        List<String> vehicleTypes = body.getList("vehicleTypes", String.class);
        // Compared as sets (order is not part of the contract); the size check catches duplicates.
        assertAll(
                () -> assertEquals(ZONES.size(), zones.size(), "zones count"),
                () -> assertEquals(ZONES, Set.copyOf(zones), "zones"),
                () -> assertEquals(VEHICLE_TYPES.size(), vehicleTypes.size(), "vehicleTypes count"),
                () -> assertEquals(VEHICLE_TYPES, Set.copyOf(vehicleTypes), "vehicleTypes"));
    }

    private static Set<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }
}
