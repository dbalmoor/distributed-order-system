package com.deepana.orderservice.commands;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CancelOrderCommand {

    private Long orderId;
    private String orderNumber;
    private String reason;
    private String traceId;
}
