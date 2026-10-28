package com.booking.tests.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Map;

/**
 * The single error shape the server returns for every 4xx/5xx. {@code fieldErrors} is present only for
 * bean-validation failures ({@code message = "Validation failed"}) and holds one message per field; when a
 * field breaks two constraints only one message survives, so tests assert the key, not the text.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiError(Instant timestamp,
                       int status,
                       String error,
                       String message,
                       String path,
                       Map<String, String> fieldErrors) {
}
