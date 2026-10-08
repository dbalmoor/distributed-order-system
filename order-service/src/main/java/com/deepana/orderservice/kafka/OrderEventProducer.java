package com.deepana.orderservice.kafka;

import com.deepana.saga.commondto.order.*;
import com.deepana.saga.commondto.base.BaseEvent;
import com.deepana.orderservice.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventProducer {

    private final OutboxWriter outboxWriter;

    public void sendOrderCreated(OrderCreatedEvent event) {
        write("order.created", event);
    }


    public void sendCancelRequested(OrderCancelRequestedEvent event) {
        send("order.cancel.requested", event);
    }

    public void sendOrderConfirmed(OrderConfirmedEvent event) {
        send("order.confirmed", event);
    }

    public void sendOrderCancelled(OrderCancelledEvent event) {
        send("order.cancelled", event);
    }

    private void send(String topic, BaseEvent event) {
        write(topic, event);
    }

    private void write(String topic, BaseEvent event) {
        outboxWriter.write("ORDER", topic, topic, event);
        log.info("{} queued in transactional outbox for order {}", topic, event.getOrderId());
    }
}
