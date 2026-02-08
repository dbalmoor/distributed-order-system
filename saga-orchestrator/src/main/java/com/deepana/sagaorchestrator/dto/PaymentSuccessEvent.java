package com.deepana.sagaorchestrator.dto;

import lombok.Data;

@Data
public class PaymentSuccessEvent {

    private Long orderId;
    private String orderNumber;
    private String traceId;

}
