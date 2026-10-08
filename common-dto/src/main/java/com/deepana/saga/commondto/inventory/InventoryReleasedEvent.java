package com.deepana.saga.commondto.inventory;

import com.deepana.saga.commondto.base.BaseEvent;
import com.deepana.saga.commondto.order.OrderItemEvent;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class InventoryReleasedEvent extends BaseEvent {

    private List<OrderItemEvent> items;
}
