package com.booking.tests.ui;

import com.booking.tests.api.model.Booking;
import com.booking.tests.api.model.BookingRequest;
import com.booking.tests.api.model.Courier;
import com.booking.tests.api.model.Shift;
import com.booking.tests.ui.components.RescheduleDialog;
import com.booking.tests.ui.pages.BookingsPage;
import com.booking.tests.ui.pages.ShiftsPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static com.booking.tests.data.CourierBuilder.aCourier;
import static com.booking.tests.data.ShiftBuilder.aShift;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * The Reschedule action and its searchable shift picker (a custom ARIA combobox, see {@code RescheduleDialog}).
 * Nothing is submitted here; these tests pin the picker's contract from the PR and the application README.
 *
 * <p>The picker lists <em>every</em> upcoming shift in the database, including other tests' shifts, so tests
 * never rely on an option's position: they address options by shift id or check relationships (the option
 * that is active is the one that gets selected).
 */
@DisplayName("Reschedule picker (UI)")
class ReschedulePickerUiTest extends BaseUiTest {

    private static final String OPTION_ID_PREFIX = "reschedule-option-";

    @Test
    @DisplayName("Reschedule is enabled for a confirmed future booking and disabled for a cancelled one")
    void rescheduleButtonFollowsBookingState() {
        Courier courier = couriers.createOk(aCourier().build());
        Booking confirmed = bookings.createOk(new BookingRequest(courier.id(), shifts.createOk(aShift().build()).id()));
        Booking cancelled = bookings.createOk(new BookingRequest(courier.id(), shifts.createOk(aShift().build()).id()));
        bookings.cancelOk(cancelled.id());

        BookingsPage bookingsPage = new ShiftsPage(page).open().goToBookings();

        assertThat(bookingsPage.rescheduleButton(confirmed.id())).isEnabled();
        assertThat(bookingsPage.rescheduleButton(cancelled.id())).isDisabled();
    }

    @Test
    @DisplayName("picker leaves out the current shift and shows a full shift as disabled and not selectable")
    void pickerExcludesCurrentShiftAndDisablesFullShift() {
        Courier courier = couriers.createOk(aCourier().build());
        Shift current = shifts.createOk(aShift().build());
        Booking booking = bookings.createOk(new BookingRequest(courier.id(), current.id()));
        Shift full = shifts.createOk(aShift().capacity(1).build());
        bookings.createOk(new BookingRequest(couriers.createOk(aCourier().build()).id(), full.id()));

        RescheduleDialog dialog = new ShiftsPage(page).open().goToBookings().reschedule(booking.id());

        assertThat(dialog.summary()).containsText("currently #" + current.id());
        // The full shift being listed proves the list has loaded, so the next count of 0 is meaningful.
        assertThat(dialog.option(full.id())).isVisible();
        assertThat(dialog.option(current.id())).hasCount(0);

        dialog.search("#" + full.id());
        assertThat(dialog.option(full.id())).isDisabled();
        assertThat(dialog.option(full.id())).containsText("Full");

        dialog.clickOptionAnyway(full.id());
        assertThat(dialog.option(full.id())).hasAttribute("aria-selected", "false");
        assertThat(dialog.confirmButton()).isDisabled();
    }

    @Test
    @DisplayName("keyboard: ArrowDown + Enter selects the active option; Escape closes the list but not the dialog")
    void keyboardSelectsAndEscapeCollapsesList() {
        Courier courier = couriers.createOk(aCourier().build());
        Booking booking = bookings.createOk(new BookingRequest(courier.id(), shifts.createOk(aShift().build()).id()));
        Shift target = shifts.createOk(aShift().build());

        RescheduleDialog dialog = new ShiftsPage(page).open().goToBookings()
                .reschedule(booking.id())
                .search("#" + target.id())
                .press("ArrowDown");

        // Other shifts whose id contains the target's digits may also match, so read which option became active.
        assertThat(dialog.searchBox()).hasAttribute("aria-activedescendant", Pattern.compile(OPTION_ID_PREFIX + "\\d+"));
        long activeShiftId = Long.parseLong(
                dialog.searchBox().getAttribute("aria-activedescendant").substring(OPTION_ID_PREFIX.length()));

        dialog.press("Enter");
        assertThat(dialog.option(activeShiftId)).hasAttribute("aria-selected", "true");
        assertThat(dialog.searchBox()).hasValue(Pattern.compile("^#" + activeShiftId + " · "));
        assertThat(dialog.list()).isHidden();
        assertThat(dialog.confirmButton()).isEnabled();

        dialog.openList();
        assertThat(dialog.list()).isVisible();
        dialog.press("Escape");
        assertThat(dialog.list()).isHidden();
        assertThat(dialog.root()).isVisible();
    }
}
