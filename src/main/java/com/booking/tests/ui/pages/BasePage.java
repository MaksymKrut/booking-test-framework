package com.booking.tests.ui.pages;

import com.booking.tests.ui.components.Toast;
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

    /** The app-wide toast; available on every page because the app has a single one. */
    public Toast toast() {
        return new Toast(page);
    }
}
