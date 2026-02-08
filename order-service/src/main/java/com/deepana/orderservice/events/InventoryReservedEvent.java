package com.deepana.orderservice.events;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryReservedEvent {
    private Long orderId;
    private String orderNumber;
    private BigDecimal amount;
    private String traceId;
}
