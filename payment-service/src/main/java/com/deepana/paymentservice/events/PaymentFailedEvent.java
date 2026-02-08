package com.deepana.paymentservice.events;

import lombok.Data;

@Data
public class PaymentFailedEvent {

    private Long orderId;
    private String orderNumber;
    private String reason;
    private String traceId;
}
