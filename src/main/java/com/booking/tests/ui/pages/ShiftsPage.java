package com.booking.tests.ui.pages;

import com.booking.tests.ui.components.BookDialog;
import com.booking.tests.ui.components.NavTabs;
import com.booking.tests.ui.components.ShiftDialog;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.Allure;

/**
 * Shifts tab: the landing section of the single-page app.
 *
 * <p>The app replaces {@code tbody.innerHTML} on every reload, so row elements go stale after any action.
 * That is why this class returns {@link Locator}s (lazy queries re-resolved on every use) and never
 * holds element handles. Rows are addressed by {@code tr[data-id]}; the app has no {@code data-testid}.
 */
public final class ShiftsPage extends BasePage {

    public ShiftsPage(Page page) {
        super(page);
    }

    /**
     * Loads the app and waits for its first {@code GET /api/shifts}. No tab click: Shifts is the default
     * section, and every tab click triggers another fetch that would re-render the table mid-test.
     * Waiting for the response (not a fixed delay) means the table is populated or about to be; the
     * retrying assertions in the test absorb the render that follows.
     */
    public ShiftsPage open() {
        Allure.step("Open Shifts page", () -> {
            page.waitForResponse(
                    response -> "GET".equals(response.request().method()) && response.url().contains("/api/shifts?"),
                    () -> page.navigate("/"));
        });
        return this;
    }

    /** Clicks the row's Book button ({@code disabled} unless the shift is OPEN) and returns the opened dialog. */
    public BookDialog bookShift(long shiftId) {
        Allure.step("Open book dialog for shift " + shiftId, () -> {
            row(shiftId).locator("button[data-action='book']").click();
        });
        return new BookDialog(page);
    }

    /** Clicks the row's Edit button ({@code disabled} unless OPEN or FULL) and returns the pre-filled dialog. */
    public ShiftDialog editShift(long shiftId) {
        Allure.step("Open edit dialog for shift " + shiftId, () -> {
            row(shiftId).locator("button[data-action='edit']").click();
        });
        return new ShiftDialog(page);
    }

    public BookingsPage goToBookings() {
        return new NavTabs(page).bookings();
    }

    public Locator table() {
        return page.locator("#shifts-table");
    }

    public Locator row(long shiftId) {
        return page.locator("#shifts-table tbody tr[data-id='" + shiftId + "']");
    }

    /** Cell 2: zone name. */
    public Locator zoneCell(long shiftId) {
        return row(shiftId).locator("td:nth-child(2)");
    }

    /** Cell 5: {@code "booked / capacity"}, e.g. {@code "1 / 2"}. Positional, used for cell asserts only. */
    public Locator bookedCell(long shiftId) {
        return row(shiftId).locator("td:nth-child(5)");
    }

    /** Cell 6: {@code span.badge.<STATUS>} with the status as text. */
    public Locator statusBadge(long shiftId) {
        return row(shiftId).locator("span.badge");
    }
}
