package com.deepana.orderservice.kafka;

import com.deepana.orderservice.service.OrderService;
import com.deepana.saga.commondto.payment.PaymentFailedEvent;
import com.deepana.saga.commondto.payment.PaymentSuccessEvent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    // ================= SUCCESS =================

    @KafkaListener(
            topics = "payment.success",
            groupId = "order-group"
    )
    public void handleSuccess(String message) throws JsonProcessingException {

        try {
            PaymentSuccessEvent event = objectMapper.readValue(message, PaymentSuccessEvent.class);

            MDC.put("traceId", event.getTraceId());

            log.info("Received payment.success: {}", event);

            orderService.handlePaymentSuccess(event);

        } catch (Exception e) {

            log.error("Failed to process payment.success", e);
            throw e;

        } finally {
            MDC.clear();
        }
    }

    // ================= FAILED =================

    @KafkaListener(
            topics = "payment.failed",
            groupId = "order-group"
    )
    public void handleFailed(String message) throws JsonProcessingException  {

        try {

            PaymentFailedEvent event = objectMapper.readValue(message, PaymentFailedEvent.class);

            MDC.put("traceId", event.getTraceId());

            log.info("Received payment.failed: {}", event);

            orderService.handlePaymentFailure(event);

        } catch (Exception e) {

            log.error("Failed to process payment.failed", e);
            throw e;

        } finally {
            MDC.clear();
        }
    }
}
