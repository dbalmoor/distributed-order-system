package com.deepana.paymentservice.kafka.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PaymentDLQListener {

    @KafkaListener(
            topics = "payment.dlq",
            groupId = "payment-dlq-group"
    )
    public void listenDLQ(String message) {

        log.error("🚨 PAYMENT DLQ MESSAGE RECEIVED");
        log.error("Payload: {}", message);
    }
}
