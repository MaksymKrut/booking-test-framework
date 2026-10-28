package com.booking.tests.ui;

import com.booking.tests.api.model.Shift;
import com.booking.tests.ui.pages.ShiftsPage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static com.booking.tests.data.ShiftBuilder.aShift;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

/**
 * Smoke: the UI is served, talks to the API, and renders live data. Assertions are Playwright's retrying
 * {@code assertThat(locator)}, which waits for the condition instead of sleeping.
 */
@Tag("smoke")
@DisplayName("Shifts page")
class ShiftsSmokeTest extends BaseUiTest {

    @Test
    @DisplayName("opens with the shifts table visible")
    void opensWithShiftsTable() {
        ShiftsPage shiftsPage = new ShiftsPage(page).open();

        assertThat(shiftsPage.table()).isVisible();
    }

    @Test
    @DisplayName("lists a shift created through the API with its zone, capacity and OPEN badge")
    void listsShiftSeededThroughApi() {
        Shift shift = shifts.createOk(aShift().capacity(3).build());

        ShiftsPage shiftsPage = new ShiftsPage(page).open();

        // Expected values come from the API response; time cells are locale-dependent and never asserted.
        assertThat(shiftsPage.row(shift.id())).isVisible();
        assertThat(shiftsPage.zoneCell(shift.id())).hasText(shift.zone());
        assertThat(shiftsPage.bookedCell(shift.id())).hasText(shift.bookedCount() + " / " + shift.capacity());
        assertThat(shiftsPage.statusBadge(shift.id())).hasText(shift.status());
    }
}
