package com.deepana.sagaorchestrator.commands;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CancelOrderCommand {

    private Long orderId;
    private String orderNumber;
    private String reason;
    private String traceId;
}
