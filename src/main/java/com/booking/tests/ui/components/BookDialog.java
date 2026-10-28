package com.booking.tests.ui.components;

import com.booking.tests.ui.pages.ShiftsPage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.SelectOption;
import io.qameta.allure.Allure;

/**
 * The native {@code <dialog id="book-dialog">}. The app opens it only after two fetches (active couriers and the
 * shift's bookings) have resolved and filled it, so "open" also means "populated".
 *
 * <p>On success the app closes the dialog, shows "Shift booked" and reloads the shifts table without waiting for
 * it. On error the dialog stays open and the toast shows the API message.
 */
public final class BookDialog {

    private final Page page;
    private final Locator root;

    /** Waits (Playwright default timeout) until the dialog is open; fails fast if it never opens. */
    public BookDialog(Page page) {
        this.page = page;
        this.root = page.locator("#book-dialog[open]");
        root.waitFor();
    }

    /** The dialog while it is open. Tests assert {@code isVisible()} / {@code isHidden()} on it (retrying). */
    public Locator root() {
        return root;
    }

    /** Picks the courier by option value (= courier id), not by label: ids are unique, names need not be. */
    public BookDialog selectCourier(long courierId) {
        Allure.step("Select courier " + courierId, () -> {
            page.locator("#book-courier").selectOption(new SelectOption().setValue(String.valueOf(courierId)));
        });
        return this;
    }

    /**
     * Clicks "Book" and waits for the {@code POST /api/bookings} response, whatever its status, so the caller
     * continues once the server has decided. Success or failure is for the test to assert (toast, dialog).
     */
    public ShiftsPage confirm() {
        Allure.step("Confirm booking", () -> {
            page.waitForResponse(
                    response -> "POST".equals(response.request().method()) && response.url().endsWith("/api/bookings"),
                    () -> page.locator("#book-confirm-btn").click());
        });
        return new ShiftsPage(page);
    }
}
