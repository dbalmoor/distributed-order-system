package com.deepana.orderservice.kafka;

import com.deepana.orderservice.events.OrderCancelledEvent;
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
    private final ObjectMapper mapper;

    public void sendOrderCreated(OrderCreatedEvent event) {

        try {

            String json = mapper.writeValueAsString(event);

            kafkaTemplate.send(
                    "order.created",
                    event.getOrderNumber(),
                    json
            );

            log.info("order.created sent {}", json);

        } catch (Exception e) {

            log.error("Failed to send order.created", e);
            throw new RuntimeException(e);
        }
    }


    public void sendOrderCancelled(OrderCancelledEvent event) {

        try {

            String json = mapper.writeValueAsString(event);

            kafkaTemplate.send(
                    "order.cancelled",
                    event.getOrderNumber(),
                    json
            );

            log.info("order.cancelled sent {}", json);

        } catch (Exception e) {

            throw new RuntimeException(e);
        }
    }


}
