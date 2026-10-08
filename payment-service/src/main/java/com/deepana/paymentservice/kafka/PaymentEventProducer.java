package com.deepana.paymentservice.kafka;

import com.deepana.paymentservice.outbox.OutboxWriter;
import com.deepana.saga.commondto.base.BaseEvent;
import com.deepana.saga.commondto.payment.PaymentRefundedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final OutboxWriter outboxWriter;

    public void sendSuccess(BaseEvent event) {
        write("payment.success", event);
    }

    public void sendFailed(BaseEvent event) {
        write("payment.failed", event);
    }

    public void sendRefunded(PaymentRefundedEvent event) {
        write("payment.refunded", event);
    }

    private void write(String topic, BaseEvent event) {
        outboxWriter.write("ORDER", topic, topic, event);
        log.info("{} queued in transactional outbox for order {}", topic, event.getOrderId());
    }
}
