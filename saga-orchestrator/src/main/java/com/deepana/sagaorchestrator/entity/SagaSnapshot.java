package com.deepana.sagaorchestrator.entity;

public record SagaSnapshot(
        String sagaId,
        Long orderId,
        SagaStatus status,
        SagaStep currentStep,
        Long version) {
}
