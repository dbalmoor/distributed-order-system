package com.deepana.saga.commondto.payment;

import com.deepana.saga.commondto.base.BaseEvent;
import com.deepana.saga.commondto.order.OrderItemEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEvent extends BaseEvent {

    private BigDecimal totalAmount;

    private String reason;

    private List<OrderItemEvent> items;
}



