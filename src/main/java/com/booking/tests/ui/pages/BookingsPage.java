package com.booking.tests.ui.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

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

    /** Cell 6: {@code span.badge.<STATUS>} with the status as text. */
    public Locator statusBadge(long bookingId) {
        return row(bookingId).locator("span.badge");
    }
}
