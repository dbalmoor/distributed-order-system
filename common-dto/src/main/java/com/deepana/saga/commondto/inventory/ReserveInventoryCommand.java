package com.deepana.saga.commondto.inventory;

import com.deepana.saga.commondto.base.BaseEvent;
import com.deepana.saga.commondto.order.OrderItemEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReserveInventoryCommand extends BaseEvent {

    private BigDecimal totalAmount;

    private List<OrderItemEvent> items;
}
