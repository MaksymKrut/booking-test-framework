package com.booking.tests.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/** {@code CourierResponse}. Inactive couriers cannot book (409) and are hidden from the UI book dialog. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Courier(Long id,
                      String name,
                      String email,
                      String phone,
                      String vehicleType,
                      boolean active,
                      Instant createdAt) {
}
