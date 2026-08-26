package com.jihedailabs.devtools.outboxverifier;

import java.time.Duration;

/**
 * Defaults picked to match the shape of a healthy outbox relay: a working relay publishes within
 * seconds, so a few minutes stuck pending means the relay is down, not just slow. A purge job
 * should run at least daily, so a day of unpurged sent rows means purging isn't wired up at all,
 * not that it just hasn't run yet.
 */
public record Thresholds(
        Duration stagnationThreshold,
        Duration bloatThreshold,
        int retryLoopThreshold
) {
    public static Thresholds defaults() {
        return new Thresholds(Duration.ofMinutes(5), Duration.ofHours(24), 5);
    }
}
