package com.booking.tests.ui.components;

import com.booking.tests.ui.pages.BookingsPage;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import io.qameta.allure.Allure;

/**
 * The native {@code <dialog id="reschedule-dialog">} with its searchable shift picker: a custom ARIA combobox
 * ({@code input#reschedule-shift-search[role=combobox]} controlling {@code ul#reschedule-shift-list[role=listbox]}),
 * not a native {@code <select>}, so it cannot be driven with {@code selectOption}.
 *
 * <p>App behaviour this class relies on (verified on the live DOM, AUT commit {@code 8a5ab94}):
 * <ul>
 *   <li>The dialog opens <em>before</em> its {@code GET /api/shifts?from=} resolves ("Loading shifts…"); the
 *       opener ({@code BookingsPage.reschedule}) waits for that response, so a returned dialog has its list.</li>
 *   <li>Options are {@code li[role=option]#reschedule-option-<shiftId>} with {@code data-shift-id}. A full shift is
 *       listed with {@code aria-disabled="true"}; the booking's current shift is not listed at all.</li>
 *   <li>Typing filters by shift id ({@code "123"} or {@code "#123"}, substring) or zone, debounced by 250 ms.</li>
 *   <li>ArrowDown/ArrowUp move {@code aria-activedescendant} over selectable options, Enter selects, Escape
 *       collapses the list without closing the dialog. A key press applies a pending filter immediately.</li>
 *   <li>Selecting fills the input with the option's label, collapses the list and enables "Reschedule".</li>
 * </ul>
 */
public final class RescheduleDialog {

    private final Page page;
    private final Locator root;

    /** Waits (Playwright default timeout) until the dialog is open; fails fast if it never opens. */
    public RescheduleDialog(Page page) {
        this.page = page;
        this.root = page.locator("#reschedule-dialog[open]");
        root.waitFor();
    }

    /** The dialog while it is open. Tests assert {@code isVisible()} / {@code isHidden()} on it (retrying). */
    public Locator root() {
        return root;
    }

    /** {@code "#<bookingId> · <courier> · currently #<shiftId> (<zone>)"}. */
    public Locator summary() {
        return page.locator("#reschedule-summary");
    }

    /**
     * The combobox input; its value is the search term, or the selected option's label after a selection. By id, not
     * by role: native {@code <select>} filters elsewhere on the page also have the implicit role {@code combobox}.
     */
    public Locator searchBox() {
        return page.locator("#reschedule-shift-search");
    }

    /** The listbox; hidden (class {@code hidden}) while collapsed. */
    public Locator list() {
        return page.locator("#reschedule-shift-list");
    }

    /** One option by shift id. Count 0 when the shift is not offered (e.g. the booking's current shift). */
    public Locator option(long shiftId) {
        return page.locator("#reschedule-option-" + shiftId);
    }

    public Locator confirmButton() {
        return page.locator("#reschedule-confirm-btn");
    }

    /**
     * Types {@code term} and waits until the debounced filter has been applied, i.e. every rendered option matches
     * the term the way the app matches it (id substring, {@code #} stripped, or zone), or "No shifts match" shows.
     *
     * <p>Why wait: an option clicked inside the 250 ms debounce is selected, but the pending filter then runs on the
     * selected label, matches nothing and re-opens the list showing "No shifts match". A user rarely clicks that
     * fast; a test always does, so the wait makes the picker state deterministic.
     */
    public RescheduleDialog search(String term) {
        Allure.step("Search shifts for \"" + term + "\"", () -> {
            searchBox().fill(term);
            page.waitForFunction("""
                    term => {
                        const t = term.trim().toLowerCase();
                        const id = t.startsWith('#') ? t.slice(1) : t;
                        const options = [...document.querySelectorAll('#reschedule-shift-list [role=option]')];
                        if (!options.length) return !!document.querySelector('#reschedule-shift-list .combobox-status');
                        return options.every(o => o.dataset.shiftId.includes(id)
                                || o.textContent.toLowerCase().includes(t));
                    }""", term);
        });
        return this;
    }

    /** Narrows the list to {@code #<shiftId>} and clicks that option (Playwright waits until it is enabled). */
    public RescheduleDialog pickShift(long shiftId) {
        search("#" + shiftId);
        Allure.step("Pick shift " + shiftId, () -> {
            option(shiftId).click();
        });
        return this;
    }

    /**
     * Clicks an option the way a user does, even if it is {@code aria-disabled}. Playwright refuses to click a
     * disabled element (it waits for it to become enabled), so {@code force} is needed to prove that clicking a
     * full shift does nothing; for selectable options use {@link #pickShift}.
     */
    public RescheduleDialog clickOptionAnyway(long shiftId) {
        Allure.step("Click option for shift " + shiftId + " (even if disabled)", () -> {
            option(shiftId).click(new Locator.ClickOptions().setForce(true));
        });
        return this;
    }

    /** Clicks the input, which (re)opens the list. */
    public RescheduleDialog openList() {
        Allure.step("Open shift list", () -> {
            searchBox().click();
        });
        return this;
    }

    /** Presses {@code key} in the combobox ({@code "ArrowDown"}, {@code "Enter"}, {@code "Escape"}, …). */
    public RescheduleDialog press(String key) {
        Allure.step("Press " + key, () -> {
            searchBox().press(key);
        });
        return this;
    }

    /**
     * Clicks "Reschedule" and waits for the {@code POST /api/bookings/{id}/reschedule} response, whatever its status.
     * On success the app closes the dialog, shows "Booking rescheduled" and reloads the table; on error the dialog
     * stays open and the toast shows the API message. Asserting which one happened is the test's job.
     */
    public BookingsPage confirm() {
        Allure.step("Confirm reschedule", () -> {
            page.waitForResponse(
                    response -> "POST".equals(response.request().method()) && response.url().endsWith("/reschedule"),
                    () -> confirmButton().click());
        });
        return new BookingsPage(page);
    }
}
