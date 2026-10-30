package com.booking.tests.ui.pages;

import com.booking.tests.ui.components.RescheduleDialog;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.Allure;

/**
 * Bookings tab. Reached through {@code NavTabs.bookings()}, which waits for the list fetch. Like the shifts
 * table, {@code tbody} is fully replaced on every load, so only lazy {@link Locator}s are exposed.
 * Rows are {@code tr[data-id=<bookingId>]}; cells: 1 id · 2 courier name · 3 {@code #shiftId} · 4 zone ·
 * 5 shift time (locale-dependent, never asserted) · 6 status badge · 7 created · 8 actions.
 */
public final class BookingsPage extends BasePage {

    public BookingsPage(Page page) {
        super(page);
    }

    public Locator row(long bookingId) {
        return page.locator("#bookings-table tbody tr[data-id='" + bookingId + "']");
    }

    /** Cell 2: courier name. */
    public Locator courierCell(long bookingId) {
        return row(bookingId).locator("td:nth-child(2)");
    }

    /** Cell 3: {@code "#<shiftId>"}. */
    public Locator shiftCell(long bookingId) {
        return row(bookingId).locator("td:nth-child(3)");
    }

    /** Cell 4: zone name. */
    public Locator zoneCell(long bookingId) {
        return row(bookingId).locator("td:nth-child(4)");
    }

    /** Cell 6: {@code span.badge.<STATUS>} with the status as text. */
    public Locator statusBadge(long bookingId) {
        return row(bookingId).locator("span.badge");
    }

    /** Cell 8: Reschedule, {@code disabled} unless the booking is CONFIRMED and its shift starts in the future. */
    public Locator rescheduleButton(long bookingId) {
        return row(bookingId).locator("button[data-action='reschedule']");
    }

    /**
     * Clicks the row's Reschedule button and waits for the picker's {@code GET /api/shifts?from=}: the dialog opens
     * before that fetch resolves, so waiting for the response is what makes the returned dialog usable.
     */
    public RescheduleDialog reschedule(long bookingId) {
        Allure.step("Open reschedule dialog for booking " + bookingId, () -> {
            page.waitForResponse(
                    response -> "GET".equals(response.request().method()) && response.url().contains("/api/shifts?from="),
                    () -> rescheduleButton(bookingId).click());
        });
        return new RescheduleDialog(page);
    }
}
