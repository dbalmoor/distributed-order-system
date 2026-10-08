package com.deepana.sagaorchestrator.entity;

import java.time.Instant;

public record SagaSnapshot(
        String sagaId,
        Long orderId,
        SagaStatus status,
        SagaStep currentStep,
        Long version,
        Instant deadlineAt,
        Instant lastHeartbeatAt,
        int retryCount,
        boolean needsAttention) {
}
