package com.deepana.orderservice.kafka;

import com.deepana.orderservice.events.PaymentFailedEvent;
import com.deepana.orderservice.events.PaymentSuccessEvent;
import com.deepana.orderservice.service.OrderService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "payment.success",
            groupId = "order-group"
    )
    public void handleSuccess(String message) throws JsonProcessingException {
        try {
            PaymentSuccessEvent event =
                    objectMapper.readValue(message, PaymentSuccessEvent.class);

            MDC.put("traceId", event.getTraceId());

            log.info("Received payment.success {}", message);

            orderService.handlePaymentSuccess(event);

        } catch (Exception e) {
            log.error("Failed to process payment.success", e);
            throw e;
        } finally {
            MDC.clear(); // important
        }
    }

    @KafkaListener(
            topics = "payment.failed",
            groupId = "order-group"
    )
    public void handleFailed(String message) throws JsonProcessingException {
        try {
            PaymentFailedEvent event =
                    objectMapper.readValue(message, PaymentFailedEvent.class);

            MDC.put("traceId", event.getTraceId());

            log.info("Received payment.failed {}", message);

            orderService.handlePaymentFailure(event);

        } catch (Exception e) {
            log.error("Failed to process payment.failed", e);
            throw e;
        }
    }
}
