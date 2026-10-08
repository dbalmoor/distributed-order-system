package com.deepana.sagaorchestrator.entity;

public record SagaTransition(SagaStatus status, SagaStep step) {
}
