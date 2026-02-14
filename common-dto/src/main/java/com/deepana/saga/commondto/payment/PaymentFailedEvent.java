package com.deepana.saga.commondto.payment;

import com.deepana.saga.commondto.base.BaseEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PaymentFailedEvent extends BaseEvent {

    private String reason;
}
