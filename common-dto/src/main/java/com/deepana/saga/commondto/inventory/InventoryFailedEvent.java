package com.deepana.saga.commondto.inventory;

import com.deepana.saga.commondto.base.BaseEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class InventoryFailedEvent extends BaseEvent {

    private String reason;
}
