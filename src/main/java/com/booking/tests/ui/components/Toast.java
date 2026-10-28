package com.booking.tests.ui.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * The app's single notification element {@code #notification} ({@code role="alert"}). It is reused for every
 * message: the text is replaced, the class becomes {@code notification success} or {@code notification error},
 * and {@code .hidden} is added after 4 s (the element is never removed).
 *
 * <p>Exposes locators only; tests assert with {@code assertThat(toast.success()).hasText(...)}, which retries,
 * so no wait is needed between the action and the check. Assert right after the action, inside the 4 s window.
 */
public final class Toast {

    private final Page page;

    public Toast(Page page) {
        this.page = page;
    }

    /**
     * The toast whatever its type. Asserting the expected text on this locator makes a failure print the text
     * that was actually shown (e.g. the API's error message), not just "element not found".
     */
    public Locator message() {
        return page.locator("#notification");
    }

    public Locator success() {
        return page.locator("#notification.success");
    }

    /** Error text is the API's {@code message} (plus {@code ": field msg"} for validation errors). */
    public Locator error() {
        return page.locator("#notification.error");
    }
}
