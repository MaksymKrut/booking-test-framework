package com.booking.tests.api.client;

import com.booking.tests.api.model.Shift;
import com.booking.tests.api.model.ShiftRequest;
import com.booking.tests.api.spec.Specs;
import io.qameta.allure.Allure;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.time.Instant;

import static io.restassured.RestAssured.given;

/** Client for {@code /shifts}. Same design rules as {@link CourierClient}. */
public final class ShiftClient {

    private static final String PATH = "/shifts";
    private static final String BY_ID = PATH + "/{id}";

    /**
     * {@code GET /shifts[?zone=&from=&to=]}; {@code null} omits a filter. {@code from}/{@code to} filter on
     * {@code startTime}, inclusive, and are sent as ISO-8601 UTC ({@code Instant.toString()}).
     * {@code zone} is a String so invalid values can be sent.
     */
    public Response list(String zone, Instant from, Instant to) {
        return Allure.step("List shifts zone=" + zone + " from=" + from + " to=" + to, () -> {
            RequestSpecification request = request();
            if (zone != null) {
                request.queryParam("zone", zone);
            }
            if (from != null) {
                request.queryParam("from", from.toString());
            }
            if (to != null) {
                request.queryParam("to", to.toString());
            }
            return request.get(PATH);
        });
    }

    public Response get(long id) {
        return Allure.step("Get shift " + id, () -> request().get(BY_ID, id));
    }

    /** {@code POST /shifts}: 201 with {@code bookedCount 0} and status OPEN; 400 for time/capacity rules. */
    public Response create(ShiftRequest body) {
        return Allure.step("Create shift " + body.zone() + " " + body.startTime() + " → " + body.endTime()
                + " cap " + body.capacity(), () -> request().body(body).post(PATH));
    }

    /** {@code PUT /shifts/{id}}: 409 once started, or when times/zone/capacity conflict with bookings. */
    public Response update(long id, ShiftRequest body) {
        return Allure.step("Update shift " + id, () -> request().body(body).put(BY_ID, id));
    }

    /** {@code DELETE /shifts/{id}}: 204, or 409 when the shift has any booking. */
    public Response delete(long id) {
        return Allure.step("Delete shift " + id, () -> request().delete(BY_ID, id));
    }

    /** {@code GET /shifts/{id}/bookings}: bookings of every status. */
    public Response bookings(long id) {
        return Allure.step("List bookings of shift " + id, () -> request().get(BY_ID + "/bookings", id));
    }

    /** Setup helper: create and require 201. */
    public Shift createOk(ShiftRequest body) {
        return create(body).then().spec(Specs.created()).extract().as(Shift.class);
    }

    /** Read helper: fetch and require 200 (used to observe side effects such as {@code bookedCount}). */
    public Shift getOk(long id) {
        return get(id).then().spec(Specs.ok()).extract().as(Shift.class);
    }

    private static RequestSpecification request() {
        return given().spec(Specs.request());
    }
}
