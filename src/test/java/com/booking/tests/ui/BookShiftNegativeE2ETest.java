package com.booking.tests.ui;

import com.booking.tests.api.model.Booking;
import com.booking.tests.api.model.BookingRequest;
import com.booking.tests.api.model.Courier;
import com.booking.tests.api.model.Shift;
import com.booking.tests.api.spec.Specs;
import com.booking.tests.ui.components.BookDialog;
import com.booking.tests.ui.pages.ShiftsPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.booking.tests.data.CourierBuilder.aCourier;
import static com.booking.tests.data.ShiftBuilder.aShift;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The same journey when the server refuses: the UI must surface the API's reason, keep the dialog open so the
 * user can pick someone else, and leave the data untouched.
 */
@DisplayName("Book a shift (UI): rejected")
class BookShiftNegativeE2ETest extends BaseUiTest {

    @Test
    @DisplayName("overlapping shift: error toast with the API reason, dialog stays open, nothing stored")
    void rejectsOverlappingShift() {
        Courier courier = couriers.createOk(aCourier().build());
        Shift booked = shifts.createOk(aShift().build());
        bookings.createOk(new BookingRequest(courier.id(), booked.id()));
        Shift overlapping = shifts.createOk(aShift().overlapping(booked).capacity(2).build());

        ShiftsPage shiftsPage = new ShiftsPage(page).open();
        BookDialog dialog = shiftsPage.bookShift(overlapping.id()).selectCourier(courier.id());
        dialog.confirm();

        assertThat(shiftsPage.toast().error()).hasText("Courier already has a booking that overlaps with this shift");
        assertThat(dialog.root()).isVisible();
        assertThat(shiftsPage.bookedCell(overlapping.id())).hasText("0 / " + overlapping.capacity());

        Booking[] stored = bookings.list(courier.id(), overlapping.id(), null)
                .then().spec(Specs.ok())
                .extract().as(Booking[].class);
        assertEquals(0, stored.length, "bookings stored for the overlapping shift");
    }
}
