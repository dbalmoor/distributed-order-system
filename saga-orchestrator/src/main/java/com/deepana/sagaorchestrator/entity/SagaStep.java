package com.deepana.sagaorchestrator.entity;

public enum SagaStep {
    RESERVE_INVENTORY,
    CHARGE_PAYMENT,
    CONFIRM_ORDER,
    CANCEL_ORDER,
    COMPLETED,
    CANCELLED
}
