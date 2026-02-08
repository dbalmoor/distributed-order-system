package com.deepana.sagaorchestrator.commands;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConfirmOrderCommand {

    private Long orderId;
    private String orderNumber;
    private String traceId;
}

