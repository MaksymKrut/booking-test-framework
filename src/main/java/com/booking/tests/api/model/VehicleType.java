package com.booking.tests.api.model;

/**
 * Courier vehicle types as published by {@code GET /meta}. Like {@link Zone}, request records use the
 * {@code String} form so invalid values can be sent on purpose.
 */
public enum VehicleType {
    BIKE, SCOOTER, CAR
}
