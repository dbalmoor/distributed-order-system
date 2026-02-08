package com.deepana.orderservice.entity;

public enum OrderStatus {

    CREATED,                  // Order created

    INVENTORY_RESERVED,       // Stock locked

    PAYMENT_SUCCESS_PENDING,
    PAYMENT_FAILED_PENDING,   // Payment failed before inventory

    FAILED,                   // Saga terminated

    CANCELLED,                // User cancelled

    COMPLETED                 // Order finished
}

