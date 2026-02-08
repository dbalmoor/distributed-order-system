package com.deepana.sagaorchestrator.kafka;

import com.deepana.sagaorchestrator.dto.*;
import com.deepana.sagaorchestrator.service.SagaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
@Slf4j
public class SagaEventConsumer {

    private final SagaService sagaService;
    private final ObjectMapper objectMapper;

    // ---------------- ORDER ----------------

    @KafkaListener(topics = "order.created", groupId = "saga-group")
    public void onOrderCreated(String message) {

        try {

            OrderCreatedEvent event =
                    objectMapper.readValue(message, OrderCreatedEvent.class);

            sagaService.handleOrderCreated(event);

        } catch (Exception e) {

            log.error("Failed to parse order.created", e);

            throw new RuntimeException(e); // for DLQ
        }
    }


    // ---------------- INVENTORY ----------------

    @KafkaListener(topics = "inventory.reserved", groupId = "saga-group")
    public void onInventoryReserved(String message) {

        try {

            InventoryReservedEvent event =
                    objectMapper.readValue(message, InventoryReservedEvent.class);

            sagaService.handleInventoryReserved(event);

        } catch (Exception e) {

            log.error("Failed to parse inventory.reserved", e);

            throw new RuntimeException(e);
        }
    }


    @KafkaListener(topics = "inventory.failed", groupId = "saga-group")
    public void onInventoryFailed(String message) {

        try {

            InventoryFailedEvent event =
                    objectMapper.readValue(message, InventoryFailedEvent.class);

            sagaService.handleInventoryFailed(event);

        } catch (Exception e) {

            log.error("Failed to parse inventory.failed", e);

            throw new RuntimeException(e);
        }
    }


    // ---------------- PAYMENT ----------------

    @KafkaListener(topics = "payment.success", groupId = "saga-group")
    public void onPaymentSuccess(String message) {

        try {

            PaymentSuccessEvent event =
                    objectMapper.readValue(message, PaymentSuccessEvent.class);

            sagaService.handlePaymentSuccess(event);

        } catch (Exception e) {

            log.error("Failed to parse payment.success", e);

            throw new RuntimeException(e);
        }
    }


    @KafkaListener(topics = "payment.failed", groupId = "saga-group")
    public void onPaymentFailed(String message) {

        try {

            PaymentFailedEvent event =
                    objectMapper.readValue(message, PaymentFailedEvent.class);

            sagaService.handlePaymentFailed(event);

        } catch (Exception e) {

            log.error("Failed to parse payment.failed", e);

            throw new RuntimeException(e);
        }
    }
}
