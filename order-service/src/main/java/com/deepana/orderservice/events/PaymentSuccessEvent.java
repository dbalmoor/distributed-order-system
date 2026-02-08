package com.deepana.orderservice.events;

import lombok.Data;

@Data
public class PaymentSuccessEvent {

    private Long orderId;
    private String orderNumber;
    private String traceId;
}
