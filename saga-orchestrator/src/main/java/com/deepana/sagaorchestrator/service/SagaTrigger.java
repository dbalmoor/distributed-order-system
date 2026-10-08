package com.deepana.sagaorchestrator.service;

public enum SagaTrigger {
    INVENTORY_RESERVED,
    INVENTORY_FAILED,
    PAYMENT_SUCCESS,
    PAYMENT_FAILED,
    ORDER_CONFIRMED,
    ORDER_CANCELLED,
    CANCEL_REQUESTED
}
