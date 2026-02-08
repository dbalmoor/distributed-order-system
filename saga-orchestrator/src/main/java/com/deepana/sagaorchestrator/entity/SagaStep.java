package com.deepana.sagaorchestrator.entity;

public enum SagaStep {
    ORDER_CREATED,
    INVENTORY_RESERVED,
    PAYMENT_DONE,
    COMPENSATED
}
