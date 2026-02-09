package com.deepana.orderservice.commands;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ConfirmOrderCommand {

    private Long orderId;
    private String orderNumber;
    private String traceId;
}

