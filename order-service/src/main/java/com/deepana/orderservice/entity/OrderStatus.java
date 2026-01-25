package com.deepana.orderservice.entity;

public enum OrderStatus{
    CREATED,          // Order created, not processed yet
    INVENTORY_PENDING,
    INVENTORY_RESERVED,
    PAYMENT_PENDING,
    PAID,
    CONFIRMED,        // Order completed successfully
    FAILED,
    CANCELLED
}
