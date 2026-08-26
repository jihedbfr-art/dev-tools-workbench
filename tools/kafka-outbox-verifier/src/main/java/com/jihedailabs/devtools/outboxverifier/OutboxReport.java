package com.jihedailabs.devtools.outboxverifier;

import java.time.Duration;
import java.util.List;

public record OutboxReport(
        int totalRows,
        List<String> stagnantRowIds,
        int bloatedCount,
        Duration latencyP50,
        Duration latencyP95,
        Duration latencyP99,
        int failedCount,
        List<String> retryLoopRowIds
) {
    /** False the moment any signal indicates something a healthy relay wouldn't produce. */
    public boolean isHealthy() {
        return stagnantRowIds.isEmpty() && bloatedCount == 0 && retryLoopRowIds.isEmpty();
    }
}
