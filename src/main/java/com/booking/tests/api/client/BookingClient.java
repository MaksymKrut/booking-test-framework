package com.booking.tests.api.client;

import com.booking.tests.api.model.Booking;
import com.booking.tests.api.model.BookingRequest;
import com.booking.tests.api.model.RescheduleRequest;
import com.booking.tests.api.spec.Specs;
import io.qameta.allure.Allure;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;

/**
 * Client for {@code /bookings}. Same design rules as {@link CourierClient}.
 * Bookings are never deleted: there is no delete method because the API has none.
 */
public final class BookingClient {

    private static final String PATH = "/bookings";
    private static final String BY_ID = PATH + "/{id}";

    /**
     * {@code GET /bookings[?courierId=&shiftId=&status=]}; {@code null} omits a filter. Sorted by
     * {@code createdAt} DESC. {@code status} is a String so invalid values can be sent.
     */
    public Response list(Long courierId, Long shiftId, String status) {
        return Allure.step("List bookings courierId=" + courierId + " shiftId=" + shiftId + " status=" + status, () -> {
            RequestSpecification request = request();
            if (courierId != null) {
                request.queryParam("courierId", courierId);
            }
            if (shiftId != null) {
                request.queryParam("shiftId", shiftId);
            }
            if (status != null) {
                request.queryParam("status", status);
            }
            return request.get(PATH);
        });
    }

    public Response get(long id) {
        return Allure.step("Get booking " + id, () -> request().get(BY_ID, id));
    }

    /**
     * {@code POST /bookings}: 201 CONFIRMED. Failures follow a fixed server-side precedence
     * (404 courier, 404 shift, 409 inactive, started, duplicate, overlap, full, then 400 nulls).
     */
    public Response create(BookingRequest body) {
        return Allure.step("Book shift " + body.shiftId() + " for courier " + body.courierId(),
                () -> request().body(body).post(PATH));
    }

    /** {@code POST /bookings/{id}/cancel} (no body): 200 CANCELLED with {@code cancelledAt}; 409 if repeated or started. */
    public Response cancel(long id) {
        return Allure.step("Cancel booking " + id, () -> request().post(BY_ID + "/cancel", id));
    }

    /**
     * {@code POST /bookings/{id}/reschedule}: 200 with the <em>same</em> booking (id, {@code createdAt}) moved to
     * {@code body.shiftId()}. A null {@code shiftId} is bean validation and answers 400 before anything else; then a
     * fixed server-side precedence (404 booking, 404 shift, 409 cancelled, same shift, inactive, started, overlap, full).
     */
    public Response reschedule(long id, RescheduleRequest body) {
        return Allure.step("Reschedule booking " + id + " to shift " + body.shiftId(),
                () -> request().body(body).post(BY_ID + "/reschedule", id));
    }

    /** Setup helper: book and require 201. */
    public Booking createOk(BookingRequest body) {
        return create(body).then().spec(Specs.created()).extract().as(Booking.class);
    }

    /** Setup helper: cancel and require 200. */
    public Booking cancelOk(long id) {
        return cancel(id).then().spec(Specs.ok()).extract().as(Booking.class);
    }

    /** Setup helper: reschedule and require 200. */
    public Booking rescheduleOk(long id, long shiftId) {
        return reschedule(id, new RescheduleRequest(shiftId)).then().spec(Specs.ok()).extract().as(Booking.class);
    }

    /** Setup helper: read and require 200. */
    public Booking getOk(long id) {
        return get(id).then().spec(Specs.ok()).extract().as(Booking.class);
    }

    private static RequestSpecification request() {
        return given().spec(Specs.request());
    }
}
