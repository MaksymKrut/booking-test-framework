package com.booking.tests.ui;

import com.booking.tests.api.model.BookingRequest;
import com.booking.tests.api.model.Courier;
import com.booking.tests.api.model.Shift;
import com.booking.tests.ui.pages.ShiftsPage;
import com.booking.tests.util.Waits;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static com.booking.tests.data.CourierBuilder.aCourier;
import static com.booking.tests.data.ShiftBuilder.aShift;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Known UI bugs, one test each, documented in {@code documents/BUGS.md}.
 *
 * <p><b>Every test here is expected to FAIL while its bug exists</b>; see {@code KnownBugsApiTest} for the
 * rules. Failures attach a screenshot and a trace, which show the actual UI state.
 */
@Tag("bugs")
@DisplayName("Known bugs (UI): expected to fail")
class KnownBugsUiTest extends BaseUiTest {

    @Test
    @DisplayName("BUG-06: editing only the capacity of a booked shift succeeds when its start has seconds")
    void capacityOnlyEditKeepsShiftTimes() {
        Courier courier = couriers.createOk(aCourier().build());
        Shift shift = shifts.createOk(aShift().plusSeconds(30).capacity(2).build());
        bookings.createOk(new BookingRequest(courier.id(), shift.id()));
        int newCapacity = shift.capacity() + 1;

        ShiftsPage shiftsPage = new ShiftsPage(page).open();
        shiftsPage.editShift(shift.id()).capacity(newCapacity).save();

        // On the generic toast so a failure prints what the user actually saw (the API's 409 message).
        assertThat(shiftsPage.toast().message()).hasText("Shift updated");
        assertThat(shiftsPage.bookedCell(shift.id())).hasText("1 / " + newCapacity);
    }

    @Test
    @DisplayName("BUG-07: a shift in progress stays visible in the default \"Upcoming only\" view (expectation assumed)")
    void inProgressShiftIsListedByDefault() {
        // Started shifts cannot be created; create one 3 s ahead and wait until the server reports it started.
        Shift shift = shifts.createOk(aShift().startingIn(Duration.ofSeconds(3)).build());
        Waits.until(() -> "IN_PROGRESS".equals(shifts.getOk(shift.id()).status()),
                Duration.ofSeconds(15), Duration.ofMillis(500), "shift " + shift.id() + " is IN_PROGRESS");

        ShiftsPage shiftsPage = new ShiftsPage(page).open();

        assertThat(shiftsPage.row(shift.id())).isVisible();
    }
}
