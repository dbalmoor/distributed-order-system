package com.deepana.orderservice.kafka;

import com.deepana.saga.commondto.order.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

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


    public void sendOrderCancelled(CancelOrderCommand command) {

        try{
            String json = objectMapper.writeValueAsString(command);
            kafkaTemplate.send(
                    "order.cancel.cmd",
                    String.valueOf(command.getOrderId()),
                    json
            );

            log.info("order.cancel.cmd sent: {}", command);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
