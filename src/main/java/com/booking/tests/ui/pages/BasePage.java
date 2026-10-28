package com.booking.tests.ui.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * Common base for page objects. Page objects expose locators and actions only, never assertions: the test
 * decides what "correct" means and asserts with Playwright's retrying {@code assertThat(locator)}.
 */
public abstract class BasePage {

    protected final Page page;

    protected BasePage(Page page) {
        this.page = page;
    }

    /**
     * The single toast element ({@code role="alert"}). The app reuses it for every message, replaces its text,
     * and hides it again after 4 s, so assert on it immediately after the action that triggers it.
     */
    public Locator toast() {
        return page.locator("#notification");
    }
}
