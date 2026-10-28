package com.booking.tests.api.model;

/**
 * Body of {@code POST/PUT /couriers}. {@code active}: {@code null} on POST means {@code true};
 * {@code null} on PUT means "leave unchanged". The server stores {@code email} lower-cased and trimmed.
 */
public record CourierRequest(String name, String email, String phone, String vehicleType, Boolean active) {
}
