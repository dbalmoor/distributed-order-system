package com.deepana.paymentservice.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void sendSuccess(Object event) {

        try {
            String json = objectMapper.writeValueAsString(event);


            kafkaTemplate.send("payment.success", json);

            log.info("Payment success sent: {}", json);

        } catch (Exception e) {
            log.error("Failed to send payment success", e);
        }
    }

    public void sendFailed(Object event) {

        try {
            String json = objectMapper.writeValueAsString(event);

            kafkaTemplate.send("payment.failed", json);

            log.info("Payment failed sent: {}", json);

        } catch (Exception e) {
            log.error("Failed to send payment failed", e);
        }
    }
}
