package com.booking.tests.ui;

import com.booking.tests.api.model.Booking;
import com.booking.tests.api.model.BookingRequest;
import com.booking.tests.api.model.Courier;
import com.booking.tests.api.model.Shift;
import com.booking.tests.api.spec.Specs;
import com.booking.tests.ui.components.RescheduleDialog;
import com.booking.tests.ui.pages.BookingsPage;
import com.booking.tests.ui.pages.ShiftsPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.booking.tests.data.CourierBuilder.aCourier;
import static com.booking.tests.data.ShiftBuilder.aShift;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Journey: a dispatcher moves a courier's confirmed booking to another shift (AUT PR #1). Data is seeded through
 * the API; the browser drives only the reschedule itself. The API is the source of truth for what was stored.
 */
@DisplayName("Reschedule a booking (UI)")
class RescheduleBookingE2ETest extends BaseUiTest {

    @Test
    @DisplayName("reschedules via the shift picker: success toast, row shows the new shift, same booking stored")
    void reschedulesBookingThroughPicker() {
        Courier courier = couriers.createOk(aCourier().build());
        Shift current = shifts.createOk(aShift().build());
        Shift target = shifts.createOk(aShift().build());
        Booking booking = bookings.createOk(new BookingRequest(courier.id(), current.id()));

        BookingsPage bookingsPage = new ShiftsPage(page).open().goToBookings()
                .reschedule(booking.id())
                .pickShift(target.id())
                .confirm();

        // Toast first: it hides after 4 s. The table reload is fire-and-forget, so wait on the cell content.
        assertThat(bookingsPage.toast().success()).hasText("Booking rescheduled");
        assertThat(bookingsPage.shiftCell(booking.id())).hasText("#" + target.id());
        assertThat(bookingsPage.zoneCell(booking.id())).hasText(target.zone());
        assertThat(bookingsPage.statusBadge(booking.id())).hasText("CONFIRMED");

        Booking stored = bookings.getOk(booking.id());
        Booking[] ofCourier = bookings.list(courier.id(), null, null)
                .then().spec(Specs.ok())
                .extract().as(Booking[].class);
        assertAll(
                () -> assertEquals(target.id(), stored.shiftId(), "stored shiftId"),
                () -> assertEquals(1, ofCourier.length, "bookings of the courier (moved, not re-created)"));
    }

    @Test
    @DisplayName("overlap with another booking: error toast with the API reason, dialog stays open, booking not moved")
    void rejectsTargetOverlappingAnotherBooking() {
        Courier courier = couriers.createOk(aCourier().build());
        Shift current = shifts.createOk(aShift().build());
        Booking moving = bookings.createOk(new BookingRequest(courier.id(), current.id()));
        Shift other = shifts.createOk(aShift().build());
        bookings.createOk(new BookingRequest(courier.id(), other.id()));
        // Overlaps the courier's *other* booking only, so the rejection is the specified rule, not BUG-11.
        Shift target = shifts.createOk(aShift().overlapping(other).build());

        BookingsPage bookingsPage = new ShiftsPage(page).open().goToBookings();
        RescheduleDialog dialog = bookingsPage.reschedule(moving.id()).pickShift(target.id());
        dialog.confirm();

        assertThat(bookingsPage.toast().error()).hasText("Courier already has a booking that overlaps with this shift");
        assertThat(dialog.root()).isVisible();
        assertThat(bookingsPage.shiftCell(moving.id())).hasText("#" + current.id());

        assertEquals(current.id(), bookings.getOk(moving.id()).shiftId(), "stored shiftId");
    }
}
