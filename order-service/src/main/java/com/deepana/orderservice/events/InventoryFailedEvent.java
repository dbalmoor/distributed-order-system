package com.deepana.orderservice.events;

import lombok.Data;

@Data
public class InventoryFailedEvent {

    private Long orderId;
    private String orderNumber;
    private String reason;
    private String traceId;
}
