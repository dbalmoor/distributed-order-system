package com.deepana.saga.commondto.order;

import com.deepana.saga.commondto.base.BaseEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class OrderCancelledEvent extends BaseEvent {
}
