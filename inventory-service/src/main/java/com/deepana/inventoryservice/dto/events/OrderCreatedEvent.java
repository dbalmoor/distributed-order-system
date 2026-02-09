package com.deepana.inventoryservice.dto.events;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderCreatedEvent {

    private Long orderId;
    private String orderNumber;
    private Long userId;
    private BigDecimal totalAmount;
    private List<OrderItemEvent> items;
    private LocalDateTime createdAt;
    private String traceId;
}
