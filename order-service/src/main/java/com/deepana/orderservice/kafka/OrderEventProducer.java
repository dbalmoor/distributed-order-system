package com.deepana.orderservice.kafka;

import com.deepana.saga.commondto.order.*;
import com.deepana.saga.commondto.base.BaseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventProducer {

    // IMPORTANT: Object, not String
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;


    public void sendOrderCreated(OrderCreatedEvent event) {

        try {
            String json = objectMapper.writeValueAsString(event);

            kafkaTemplate.send(
                    "order.created",
                    String.valueOf(event.getOrderId()),
                    json
            );

            log.info("order.created sent: {}", json);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, String.valueOf(event.getOrderId()), json)
                    .get(10, TimeUnit.SECONDS);
            log.info("{} sent: {}", topic, json);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while publishing {}", topic, e);
            throw new IllegalStateException("Interrupted while publishing " + topic, e);
        } catch (Exception e) {
            log.error("Failed to publish {}", topic, e);
            throw new IllegalStateException("Failed to publish " + topic, e);
        }
    }
}
