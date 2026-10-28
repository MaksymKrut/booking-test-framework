package com.booking.tests.api;

import com.booking.tests.api.client.BookingClient;
import com.booking.tests.api.client.CourierClient;
import com.booking.tests.api.client.ShiftClient;
import org.junit.jupiter.api.Tag;

/**
 * Base for API tests: the {@code api} tag ({@code @Tag} is inherited, so {@code -Dgroups=api} selects every
 * subclass) and one instance of each client. Clients are stateless, so sharing them per test instance is
 * safe under the parallel-classes setting in {@code junit-platform.properties}.
 */
@Tag("api")
public abstract class BaseApiTest {

    /** Classpath location of the {@code BookingResponse} contract, shared by create and cancel tests. */
    protected static final String BOOKING_SCHEMA = "schemas/booking.schema.json";

    protected final CourierClient couriers = new CourierClient();
    protected final ShiftClient shifts = new ShiftClient();
    protected final BookingClient bookings = new BookingClient();
}
