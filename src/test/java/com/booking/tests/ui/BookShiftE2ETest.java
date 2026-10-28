package com.booking.tests.ui;

import com.booking.tests.api.model.Booking;
import com.booking.tests.api.model.Courier;
import com.booking.tests.api.model.Shift;
import com.booking.tests.api.spec.Specs;
import com.booking.tests.ui.pages.BookingsPage;
import com.booking.tests.ui.pages.ShiftsPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.booking.tests.data.CourierBuilder.aCourier;
import static com.booking.tests.data.ShiftBuilder.aShift;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Critical journey: a dispatcher books an open shift for a courier.
 * Data is seeded through the API; only the journey itself goes through the browser. The API is then used as
 * the source of truth for what was stored (and to learn the booking id, which the shifts view does not show).
 */
@Tag("smoke")
@DisplayName("Book a shift (UI)")
class BookShiftE2ETest extends BaseUiTest {

    @Test
    @DisplayName("books an open shift: success toast, booked count, stored CONFIRMED, listed in Bookings")
    void booksOpenShift() {
        Courier courier = couriers.createOk(aCourier().build());
        Shift shift = shifts.createOk(aShift().capacity(2).build());

        ShiftsPage shiftsPage = new ShiftsPage(page).open()
                .bookShift(shift.id())
                .selectCourier(courier.id())
                .confirm();

        // Toast first: it hides after 4 s. The table reload is fire-and-forget, so wait on the cell content.
        assertThat(shiftsPage.toast().success()).hasText("Shift booked");
        assertThat(shiftsPage.bookedCell(shift.id())).hasText("1 / " + shift.capacity());

        Booking[] stored = bookings.list(courier.id(), shift.id(), null)
                .then().spec(Specs.ok())
                .extract().as(Booking[].class);
        assertEquals(1, stored.length, "bookings stored for this courier and shift");
        Booking booking = stored[0];
        assertEquals("CONFIRMED", booking.status(), "stored status");

        BookingsPage bookingsPage = shiftsPage.goToBookings();
        assertThat(bookingsPage.statusBadge(booking.id())).hasText("CONFIRMED");
        assertThat(bookingsPage.courierCell(booking.id())).hasText(courier.name());
    }
}
