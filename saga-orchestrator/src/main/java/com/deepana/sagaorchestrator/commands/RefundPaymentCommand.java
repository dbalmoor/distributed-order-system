package com.deepana.sagaorchestrator.commands;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RefundPaymentCommand {

    private Long orderId;

    private String orderNumber;

    private Double amount;

    // Distributed tracing
    private String traceId;

    // Why refund happened
    private String reason;

    // For idempotency (important later)
    private String requestId;


    // Helper constructor
    public static RefundPaymentCommand create(
            Long orderId,
            String orderNumber,
            Double amount,
            String traceId,
            String reason
    ) {

        return new RefundPaymentCommand(
                orderId,
                orderNumber,
                amount,
                traceId,
                reason,
                UUID.randomUUID().toString()
        );
    }
}
