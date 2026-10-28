package com.booking.tests.ui.driver;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import io.qameta.allure.Allure;
import io.qameta.allure.AttachmentOptions;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * JUnit 5 lifecycle for UI tests: one fresh page per test, failure evidence in Allure, clean shutdown.
 *
 * <p>Callback order matters. {@link AfterTestExecutionCallback} runs right after the test body and
 * <em>before</em> any {@code @AfterEach}, while the page is still alive and still shows the failing state;
 * that is where the screenshot and trace are taken. {@link AfterEachCallback} then closes the context,
 * {@link AfterAllCallback} the browser.
 *
 * <p>Evidence collection never throws: if the page crashed and a screenshot is impossible, the problem is
 * logged and the original test failure stays the one JUnit reports.
 */
public final class UiTestExtension
        implements BeforeEachCallback, AfterTestExecutionCallback, AfterEachCallback, AfterAllCallback {

    private static final Logger log = LoggerFactory.getLogger(UiTestExtension.class);
    private static final Path TRACES = Path.of("target", "traces");

    @Override
    public void beforeEach(ExtensionContext context) {
        PlaywrightManager.openPage();
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        if (context.getExecutionException().isEmpty()) {
            return;
        }
        String name = context.getRequiredTestClass().getSimpleName() + "." + context.getRequiredTestMethod().getName()
                + "-" + UUID.randomUUID().toString().substring(0, 8); // parameterized runs share a method name
        attachScreenshot();
        attachTrace(TRACES.resolve(name + ".zip"));
    }

    @Override
    public void afterEach(ExtensionContext context) {
        PlaywrightManager.closePage();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        PlaywrightManager.closeBrowser();
    }

    private static void attachScreenshot() {
        try {
            byte[] png = PlaywrightManager.page().screenshot(new Page.ScreenshotOptions().setFullPage(true));
            Allure.attachment("Screenshot on failure", "image/png", new ByteArrayInputStream(png),
                    AttachmentOptions.withFileExtension("png"));
        } catch (RuntimeException e) {
            log.warn("Could not capture failure screenshot", e);
        }
    }

    /** Saves the trace under target/traces (kept for show-trace) and attaches the same zip to Allure. */
    private static void attachTrace(Path trace) {
        try {
            Files.createDirectories(trace.getParent());
            PlaywrightManager.context().tracing().stop(new Tracing.StopOptions().setPath(trace));
            try (InputStream in = Files.newInputStream(trace)) {
                Allure.attachment("Playwright trace", "application/zip", in, AttachmentOptions.withFileExtension("zip"));
            }
            log.info("Trace saved to {} (view: https://trace.playwright.dev or Playwright CLI show-trace)", trace);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not save Playwright trace", e);
        }
    }
}
