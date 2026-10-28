package com.booking.tests.data;

import com.booking.tests.api.model.CourierRequest;
import com.booking.tests.api.model.VehicleType;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Test-data builder for {@link CourierRequest}. Defaults are valid and unique per call, so a test only
 * states what matters to it ({@code aCourier().inactive().build()}) and never collides with seed rows,
 * other tests, or earlier runs (the database is never reset and emails must be unique).
 *
 * <p>Defaults: name {@code "QA Courier <id>"}, email {@code qa-<id>@example.com}, phone
 * {@code +1-555-NNNN} (matches the server regex), random vehicle type, active. {@code <id>} is the first
 * 8 hex chars of a random UUID.
 */
public final class CourierBuilder {

    private String name;
    private String email;
    private String phone;
    private VehicleType vehicle;
    private Boolean active = true;

    private CourierBuilder() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        String id = UUID.randomUUID().toString().substring(0, 8);
        name = "QA Courier " + id;
        email = "qa-" + id + "@example.com";
        phone = String.format("+1-555-%04d", random.nextInt(10_000));
        VehicleType[] vehicles = VehicleType.values();
        vehicle = vehicles[random.nextInt(vehicles.length)];
    }

    public static CourierBuilder aCourier() {
        return new CourierBuilder();
    }

    public CourierBuilder name(String name) {
        this.name = name;
        return this;
    }

    /** Overrides the valid default, e.g. to probe the phone rule ({@code ^\+?[0-9][0-9\- ]{6,19}$}). */
    public CourierBuilder phone(String phone) {
        this.phone = phone;
        return this;
    }

    public CourierBuilder vehicle(VehicleType vehicle) {
        this.vehicle = vehicle;
        return this;
    }

    /** Inactive couriers can be created directly; they are rejected with 409 when booking. */
    public CourierBuilder inactive() {
        this.active = false;
        return this;
    }

    public CourierRequest build() {
        return new CourierRequest(name, email, phone, vehicle.name(), active);
    }
}
