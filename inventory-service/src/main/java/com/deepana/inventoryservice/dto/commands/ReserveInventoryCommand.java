package com.deepana.inventoryservice.dto.commands;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReserveInventoryCommand {

    private Long orderId;
    private String orderNumber;
    private BigDecimal totalAmount;
    private String traceId;
}