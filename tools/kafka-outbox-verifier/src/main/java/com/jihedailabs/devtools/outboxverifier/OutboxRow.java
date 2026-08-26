package com.jihedailabs.devtools.outboxverifier;

import java.time.Instant;

record OutboxRow(String id, Instant createdAt, Instant completedAt, Instant failedAt, Integer retryCount) {

    boolean isPending() {
        return completedAt == null && failedAt == null;
    }

    boolean isCompleted() {
        return completedAt != null;
    }

    boolean isFailed() {
        return failedAt != null;
    }
}
