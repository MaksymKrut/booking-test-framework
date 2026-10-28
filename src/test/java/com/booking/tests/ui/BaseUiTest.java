package com.booking.tests.ui;

import com.booking.tests.api.client.BookingClient;
import com.booking.tests.api.client.CourierClient;
import com.booking.tests.api.client.ShiftClient;
import com.booking.tests.ui.driver.PlaywrightManager;
import com.booking.tests.ui.driver.UiTestExtension;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Base for UI tests: the {@code ui} tag, the Playwright lifecycle ({@link UiTestExtension}), and the API
 * clients. UI tests seed their data through the API (fast, deterministic) and use the browser only for
 * the journey under test.
 */
@ExtendWith(UiTestExtension.class)
@Tag("ui")
public abstract class BaseUiTest {

    protected final CourierClient couriers = new CourierClient();
    protected final ShiftClient shifts = new ShiftClient();
    protected final BookingClient bookings = new BookingClient();

    protected Page page;

    /** Extension callbacks run before {@code @BeforeEach} methods, so the page already exists here. */
    @BeforeEach
    void bindPage() {
        page = PlaywrightManager.page();
    }
}
