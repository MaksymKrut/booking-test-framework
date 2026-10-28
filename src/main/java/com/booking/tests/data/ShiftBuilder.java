package com.booking.tests.data;

import com.booking.tests.api.model.Shift;
import com.booking.tests.api.model.ShiftRequest;
import com.booking.tests.api.model.Zone;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test-data builder for {@link ShiftRequest}. Defaults: random zone, capacity 2, 4 hours long, starting
 * far in the future so the shift never ages into IN_PROGRESS/COMPLETED during a run.
 *
 * <p><b>Why default shifts never overlap.</b> Each call takes its own calendar day after 2030-01-01 from an
 * atomic counter (thread-safe under parallel classes) seeded randomly per JVM, so runs spread across
 * decades. Within that day the start hour is random in 00–11, and the longest legal shift is 12 h, so a
 * default shift always ends on its own day. Two default shifts therefore cannot overlap, and a test
 * only gets an overlap when it asks for one ({@link #overlapping(Shift)}).
 */
public final class ShiftBuilder {

    private static final Instant BASE = Instant.parse("2030-01-01T00:00:00Z");
    private static final AtomicLong NEXT_DAY = new AtomicLong(ThreadLocalRandom.current().nextLong(30_000));

    private Zone zone;
    private Instant start;
    private Duration duration = Duration.ofHours(4);
    private int capacity = 2;

    private ShiftBuilder() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Zone[] zones = Zone.values();
        zone = zones[random.nextInt(zones.length)];
        start = BASE.plus(NEXT_DAY.getAndIncrement(), ChronoUnit.DAYS)
                .plus(random.nextInt(12), ChronoUnit.HOURS);
    }

    public static ShiftBuilder aShift() {
        return new ShiftBuilder();
    }

    /** Server accepts 1–50; values outside are allowed here for boundary tests. */
    public ShiftBuilder capacity(int capacity) {
        this.capacity = capacity;
        return this;
    }

    public ShiftBuilder zone(Zone zone) {
        this.zone = zone;
        return this;
    }

    public ShiftBuilder startingAt(Instant start) {
        this.start = start;
        return this;
    }

    /**
     * Starts {@code fromNow} after the current time. Shifts in the past cannot be created, so this is the only
     * way to get a shift that has started: create it a few seconds ahead, then wait for the server to report
     * IN_PROGRESS. Assumes the test host and the server share a clock (both run locally).
     */
    public ShiftBuilder startingIn(Duration fromNow) {
        this.start = Instant.now().plus(fromNow);
        return this;
    }

    /**
     * Moves the start by {@code seconds} (the duration is kept). Default starts are on the full hour; the API
     * accepts any second, the UI's {@code datetime-local} inputs only minutes, so this exposes that mismatch.
     */
    public ShiftBuilder plusSeconds(long seconds) {
        this.start = start.plusSeconds(seconds);
        return this;
    }

    /** Server accepts 1–12 h inclusive. */
    public ShiftBuilder durationHours(long hours) {
        return duration(Duration.ofHours(hours));
    }

    /** Minute precision for boundary tests (59 min, 12 h 01 min); {@link Duration#ZERO} gives end == start. */
    public ShiftBuilder duration(Duration duration) {
        this.duration = duration;
        return this;
    }

    /**
     * Starts one minute before {@code other} ends: the smallest overlap the server must reject
     * (its intervals are half-open, so anything less is back-to-back).
     */
    public ShiftBuilder overlapping(Shift other) {
        this.start = other.endTime().minus(1, ChronoUnit.MINUTES);
        return this;
    }

    /** Starts exactly when {@code other} ends: allowed for the same courier (half-open intervals). */
    public ShiftBuilder backToBack(Shift other) {
        this.start = other.endTime();
        return this;
    }

    /** Starts one day ago. The server rejects it on create (400); used for that negative case. */
    public ShiftBuilder inThePast() {
        this.start = Instant.now().truncatedTo(ChronoUnit.HOURS).minus(1, ChronoUnit.DAYS);
        return this;
    }

    public ShiftRequest build() {
        return new ShiftRequest(zone.name(), start, start.plus(duration), capacity);
    }
}
