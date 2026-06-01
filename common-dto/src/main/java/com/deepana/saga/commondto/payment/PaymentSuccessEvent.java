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
public class PaymentSuccessEvent extends BaseEvent {
    private BigDecimal totalAmount;

    private List<OrderItemEvent> items;
}
