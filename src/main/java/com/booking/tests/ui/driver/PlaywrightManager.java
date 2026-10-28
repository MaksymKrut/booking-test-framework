package com.booking.tests.ui.driver;

import com.booking.tests.config.Config;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Owns the Playwright object graph for UI tests.
 *
 * <p><b>Threading model.</b> Playwright for Java objects are not thread-safe, and JUnit runs test classes
 * concurrently ({@code junit-platform.properties}). So each worker thread gets its own {@link Playwright}
 * and {@link Browser} (expensive, created lazily, reused by every test on that thread), and each test gets
 * its own {@link BrowserContext} + {@link Page} (cheap, fully isolated: cookies, storage, trace).
 * Everything lives in {@link ThreadLocal}s; methods of one class run on the same thread
 * ({@code mode.default=same_thread}), so a test always finds the page its {@code @BeforeEach} opened.
 *
 * <p><b>Tracing</b> starts with every context so a failure can be replayed step by step. Saving is the
 * extension's job ({@link UiTestExtension}); a trace that is not saved is discarded when the context closes.
 */
public final class PlaywrightManager {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightManager.class);

    private static final ThreadLocal<Playwright> PLAYWRIGHT = new ThreadLocal<>();
    private static final ThreadLocal<Browser> BROWSER = new ThreadLocal<>();
    private static final ThreadLocal<BrowserContext> CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<Page> PAGE = new ThreadLocal<>();

    private PlaywrightManager() {
    }

    /** New isolated context (base URL from config, tracing on) and page for the current test. */
    public static Page openPage() {
        closePage(); // defensive: never leak a context left over by an aborted test on this thread
        BrowserContext context = browser().newContext(new Browser.NewContextOptions()
                .setBaseURL(Config.get().uiBaseUrl())
                .setViewportSize(1280, 800));
        context.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true)
                .setSnapshots(true));
        Page page = context.newPage();
        CONTEXT.set(context);
        PAGE.set(page);
        return page;
    }

    public static Page page() {
        return require(PAGE.get(), "page");
    }

    public static BrowserContext context() {
        return require(CONTEXT.get(), "browser context");
    }

    /** Closes the test's context (and with it the page and any unsaved trace). Safe to call twice. */
    public static void closePage() {
        BrowserContext context = CONTEXT.get();
        CONTEXT.remove();
        PAGE.remove();
        if (context != null) {
            context.close();
        }
    }

    /** Closes this thread's browser and Playwright driver; the next {@link #openPage()} relaunches them. */
    public static void closeBrowser() {
        closePage();
        Browser browser = BROWSER.get();
        Playwright playwright = PLAYWRIGHT.get();
        BROWSER.remove();
        PLAYWRIGHT.remove();
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    private static Browser browser() {
        Browser browser = BROWSER.get();
        if (browser == null) {
            Config config = Config.get();
            Playwright playwright = Playwright.create();
            PLAYWRIGHT.set(playwright);
            browser = browserType(playwright, config.browser())
                    .launch(new BrowserType.LaunchOptions().setHeadless(config.headless()));
            BROWSER.set(browser);
            log.info("Launched {} {} (headless={})", config.browser(), browser.version(), config.headless());
        }
        return browser;
    }

    private static BrowserType browserType(Playwright playwright, String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "chromium" -> playwright.chromium();
            case "firefox" -> playwright.firefox();
            case "webkit" -> playwright.webkit();
            default -> throw new IllegalStateException(
                    "Unsupported ui.browser '" + name + "' (expected chromium, firefox or webkit)");
        };
    }

    private static <T> T require(T value, String what) {
        if (value == null) {
            throw new IllegalStateException("No " + what + " open on this thread; "
                    + "is the test class extended with UiTestExtension?");
        }
        return value;
    }
}
