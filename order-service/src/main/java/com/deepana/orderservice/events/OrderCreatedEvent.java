package com.deepana.orderservice.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {

    private Long orderId;

    private String orderNumber;

    private Long userId;

    private BigDecimal totalAmount;

    private List<OrderItemEvent> items;

    private LocalDateTime createdAt;

    private String traceId;

    public OrderCreatedEvent(Long id, String orderNumber, Long userId, BigDecimal totalAmount, List<OrderItemEvent> items, LocalDateTime createdAt) {
    }
}
