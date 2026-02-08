package com.deepana.orderservice.kafka.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderDLQListener {

    @KafkaListener(
            topics = "order.dlq",
            groupId = "order-dlq-group"
    )
    public void listenDLQ(String message) {

        log.error("🚨 ORDER DLQ MESSAGE RECEIVED");
        log.error("Payload: {}", message);
    }
}
