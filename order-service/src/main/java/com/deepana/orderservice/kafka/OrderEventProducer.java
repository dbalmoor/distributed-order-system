package com.deepana.orderservice.kafka;

import com.deepana.orderservice.events.OrderCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final String TOPIC = "order.created";

    public void sendOrderCreatedEvent(Object event) {

        try {
            String json = objectMapper.writeValueAsString(event);

            kafkaTemplate.send(TOPIC, json);

            log.info("Order event sent: {}", json);

        } catch (Exception e) {
            log.error("Failed to send order event", e);
        }
    }
}
