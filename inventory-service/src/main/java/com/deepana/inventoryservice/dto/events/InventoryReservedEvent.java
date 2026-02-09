package com.deepana.inventoryservice.dto.events;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class InventoryReservedEvent {
    private String orderNumber;
    private Long orderId;
    private BigDecimal amount;
    private String traceId;
}

