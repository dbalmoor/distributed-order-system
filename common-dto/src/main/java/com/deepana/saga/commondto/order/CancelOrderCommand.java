package com.deepana.saga.commondto.order;

import com.deepana.saga.commondto.base.BaseEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CancelOrderCommand extends BaseEvent {

    private String reason;
}
