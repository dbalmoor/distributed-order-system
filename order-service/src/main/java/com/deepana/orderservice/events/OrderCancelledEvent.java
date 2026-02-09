package com.deepana.orderservice.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancelledEvent {

    private Long orderId;

    private String orderNumber;

    // Why it was cancelled (INVENTORY_FAILED / PAYMENT_FAILED / MANUAL etc.)
    private String reason;

    // For distributed tracing
    private String traceId;

    // When cancelled
    private LocalDateTime cancelledAt;

    public OrderCancelledEvent(Long id, String orderNumber, String reason, String traceId) {
    }
}
