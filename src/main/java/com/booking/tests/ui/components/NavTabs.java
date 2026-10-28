package com.booking.tests.ui.components;

import com.booking.tests.ui.pages.BookingsPage;
import com.booking.tests.ui.pages.ShiftsPage;
import com.microsoft.playwright.Page;
import io.qameta.allure.Allure;

/**
 * Top navigation. Every tab click makes the app fetch that section's data and re-render its table, so each
 * click is wrapped in {@code waitForResponse} for that fetch: the returned page is loaded, not just shown.
 * The list URLs always carry a query string ({@code /api/shifts?...}, {@code /api/bookings?...}), which
 * keeps them distinct from {@code /api/shifts/{id}/bookings}.
 */
public final class NavTabs {

    private final Page page;

    public NavTabs(Page page) {
        this.page = page;
    }

    public ShiftsPage shifts() {
        open("Shifts", "#tab-shifts", "/api/shifts?");
        return new ShiftsPage(page);
    }

    public BookingsPage bookings() {
        open("Bookings", "#tab-bookings", "/api/bookings?");
        return new BookingsPage(page);
    }

    private void open(String name, String tab, String listUrl) {
        Allure.step("Open " + name + " tab", () -> {
            page.waitForResponse(
                    response -> "GET".equals(response.request().method()) && response.url().contains(listUrl),
                    () -> page.locator(tab).click());
        });
    }
}
