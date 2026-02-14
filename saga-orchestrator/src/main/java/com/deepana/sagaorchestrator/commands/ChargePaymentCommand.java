package com.deepana.sagaorchestrator.commands;

import lombok.*;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChargePaymentCommand {

    private Long orderId;
    private String orderNumber;
    private BigDecimal totalAmount;
    private String traceId;
}
