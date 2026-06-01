package com.deepana.saga.commondto.inventory;

import com.deepana.saga.commondto.base.BaseEvent;
import com.deepana.saga.commondto.order.OrderItemEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseInventoryCommand extends BaseEvent {

    // Business fields
    private List<OrderItemEvent> items;
}
