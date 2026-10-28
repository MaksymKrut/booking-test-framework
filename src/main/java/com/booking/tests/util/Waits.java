package com.booking.tests.util;

import io.qameta.allure.Allure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

/**
 * Fluent poller for conditions outside the DOM (Playwright has its own auto-waiting for those), e.g.
 * "the server reports this shift as IN_PROGRESS".
 *
 * <p>Why this is not a {@code Thread.sleep} in disguise: the project bans fixed sleeps as a way to
 * synchronise with the system under test. Here the wait ends as soon as the condition holds, is bounded
 * by {@code timeout}, and fails with a description of what never happened. The short park between
 * polls only keeps the loop from hammering the server; it is the one place in the framework that pauses.
 */
public final class Waits {

    private static final Logger log = LoggerFactory.getLogger(Waits.class);

    private Waits() {
    }

    /**
     * Polls {@code condition} every {@code poll} until it returns {@code true}.
     *
     * @throws IllegalStateException when {@code timeout} elapses first. This is a setup/precondition
     *                               problem, not an assertion failure, hence not an {@code AssertionError}.
     */
    public static void until(BooleanSupplier condition, Duration timeout, Duration poll, String description) {
        Allure.step("Wait until " + description, () -> {
            long started = System.nanoTime();
            long deadline = started + timeout.toNanos();
            while (!condition.getAsBoolean()) {
                if (System.nanoTime() >= deadline) {
                    throw new IllegalStateException("Timed out after " + timeout + " waiting until " + description);
                }
                LockSupport.parkNanos(poll.toNanos());
            }
            log.debug("Waited {} ms until {}", Duration.ofNanos(System.nanoTime() - started).toMillis(), description);
        });
    }
}
