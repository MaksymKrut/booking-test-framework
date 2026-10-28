package com.booking.tests.api.client;

import com.booking.tests.api.model.Courier;
import com.booking.tests.api.model.CourierRequest;
import com.booking.tests.api.spec.Specs;
import io.qameta.allure.Allure;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;

/**
 * Client for {@code /couriers}.
 *
 * <p>Design shared by all clients:
 * <ul>
 *   <li>One method per endpoint, returning the raw {@link Response}. The client never asserts: the test
 *       decides which status and body it expects, so the same method serves happy and negative cases.</li>
 *   <li>{@code *Ok} helpers exist for <em>test setup</em> only. They apply the happy-path {@link Specs}
 *       and return the record, so a broken precondition fails at the line that created it rather than
 *       later with a misleading error.</li>
 *   <li>Every call runs inside an {@link Allure#step}; the Allure REST Assured filter attaches the HTTP
 *       exchange to that step, and {@code Slf4jLogFilter} logs it.</li>
 *   <li>Stateless and thread-safe: a fresh request spec is built per call.</li>
 * </ul>
 */
public final class CourierClient {

    private static final String PATH = "/couriers";
    private static final String BY_ID = PATH + "/{id}";

    /** {@code GET /couriers[?active=]}; {@code null} omits the filter. Sorted by name. */
    public Response list(Boolean active) {
        return Allure.step("List couriers" + (active == null ? "" : " active=" + active), () -> {
            RequestSpecification request = request();
            if (active != null) {
                request.queryParam("active", active);
            }
            return request.get(PATH);
        });
    }

    public Response get(long id) {
        return Allure.step("Get courier " + id, () -> request().get(BY_ID, id));
    }

    /** {@code POST /couriers}: 201 on success, 400 field errors, 409 on duplicate email (case-insensitive). */
    public Response create(CourierRequest body) {
        return Allure.step("Create courier " + body.email(), () -> request().body(body).post(PATH));
    }

    /** {@code PUT /couriers/{id}}; {@code active = null} leaves the flag unchanged. */
    public Response update(long id, CourierRequest body) {
        return Allure.step("Update courier " + id, () -> request().body(body).put(BY_ID, id));
    }

    /** {@code DELETE /couriers/{id}}: 204, or 409 when the courier has any booking (cancelled ones count). */
    public Response delete(long id) {
        return Allure.step("Delete courier " + id, () -> request().delete(BY_ID, id));
    }

    /** {@code GET /couriers/{id}/bookings}: all bookings of the courier, 404 for an unknown courier. */
    public Response bookings(long id) {
        return Allure.step("List bookings of courier " + id, () -> request().get(BY_ID + "/bookings", id));
    }

    /** Setup helper: create and require 201. */
    public Courier createOk(CourierRequest body) {
        return create(body).then().spec(Specs.created()).extract().as(Courier.class);
    }

    private static RequestSpecification request() {
        return given().spec(Specs.request());
    }
}
