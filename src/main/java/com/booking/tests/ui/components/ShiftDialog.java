package com.booking.tests.ui.components;

import com.booking.tests.ui.pages.ShiftsPage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.Allure;

/**
 * The native {@code <dialog id="shift-dialog">} used for both "New shift" and "Edit shift #N". When editing,
 * the app pre-fills the form from the shift; start and end go into {@code datetime-local} inputs, which hold
 * minutes only (seconds are dropped). Save sends the whole form ({@code PUT /api/shifts/{id}} or
 * {@code POST /api/shifts}).
 *
 * <p>Only the fields a test needs are exposed; seeding shifts goes through the API, not this dialog.
 */
public final class ShiftDialog {

    private final Page page;
    private final Locator root;

    /** Waits until the dialog is open. */
    public ShiftDialog(Page page) {
        this.page = page;
        this.root = page.locator("#shift-dialog[open]");
        root.waitFor();
    }

    public Locator root() {
        return root;
    }

    public ShiftDialog capacity(int capacity) {
        Allure.step("Set capacity " + capacity, () -> {
            page.locator("#shift-capacity").fill(String.valueOf(capacity));
        });
        return this;
    }

    /** Clicks Save and waits for the server's answer to the write (PUT on edit, POST on create). */
    public ShiftsPage save() {
        Allure.step("Save shift", () -> {
            page.waitForResponse(
                    response -> !"GET".equals(response.request().method()) && response.url().contains("/api/shifts"),
                    () -> page.locator("#shift-save-btn").click());
        });
        return new ShiftsPage(page);
    }
}
