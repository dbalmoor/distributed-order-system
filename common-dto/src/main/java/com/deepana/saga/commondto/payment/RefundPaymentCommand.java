package com.deepana.saga.commondto.payment;

import com.deepana.saga.commondto.base.BaseEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class RefundPaymentCommand extends BaseEvent {

    private BigDecimal totalAmount;

    private String reason;

    private String requestId;
}
