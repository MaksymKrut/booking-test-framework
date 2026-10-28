package com.booking.tests.api;

import com.booking.tests.api.spec.Specs;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("api")
@Tag("smoke")
@DisplayName("GET /meta")
class MetaApiTest {

    private static final Set<String> ZONES = Set.of("DOWNTOWN", "NORTH", "SOUTH", "EAST", "WEST");
    private static final Set<String> VEHICLE_TYPES = Set.of("BIKE", "SCOOTER", "CAR");

    @Test
    @DisplayName("returns all zones and vehicle types within the response-time ceiling")
    void returnsReferenceData() {
        JsonPath body = given().spec(Specs.request())
                .when().get("/meta")
                .then().spec(Specs.ok())
                .extract().jsonPath();

        List<String> zones = body.getList("zones", String.class);
        List<String> vehicleTypes = body.getList("vehicleTypes", String.class);
        assertAll(
                () -> assertEquals(ZONES.size(), zones.size(), "zones count"),
                () -> assertEquals(ZONES, Set.copyOf(zones), "zones"),
                () -> assertEquals(VEHICLE_TYPES.size(), vehicleTypes.size(), "vehicleTypes count"),
                () -> assertEquals(VEHICLE_TYPES, Set.copyOf(vehicleTypes), "vehicleTypes"));
    }
}
