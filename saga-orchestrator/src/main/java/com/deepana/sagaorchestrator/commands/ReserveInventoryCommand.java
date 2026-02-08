package com.deepana.sagaorchestrator.commands;

import com.deepana.sagaorchestrator.dto.OrderItemEvent;
import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ReserveInventoryCommand {

    private Long orderId;
    private String orderNumber;
    private BigDecimal totalAmount;
    private String traceId;
}