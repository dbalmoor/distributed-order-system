package com.deepana.saga.commondto.inventory;

import com.deepana.saga.commondto.base.BaseEvent;
import com.deepana.saga.commondto.order.OrderItemEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
public class ReserveInventoryCommand extends BaseEvent {

    private BigDecimal totalAmount;

    private List<OrderItemEvent> items;
}
