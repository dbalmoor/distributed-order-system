package com.deepana.sagaorchestrator.dto;

import lombok.Data;

@Data
public class PaymentFailedEvent {

    private Long orderId;
    private String orderNumber;
    private String reason;
    private String traceId;
}
