package com.deepana.sagaorchestrator.repository;

import com.deepana.sagaorchestrator.entity.SagaStep;

import java.time.Instant;

public record SagaAdminView(
        String sagaId,
        Long orderId,
        SagaStep currentStep,
        int retryCount,
        Instant deadlineAt,
        Instant lastHeartbeatAt) {
}
