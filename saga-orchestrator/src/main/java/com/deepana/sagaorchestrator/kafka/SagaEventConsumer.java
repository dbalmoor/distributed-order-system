package com.deepana.sagaorchestrator.kafka;


import com.deepana.saga.commondto.inventory.InventoryFailedEvent;
import com.deepana.saga.commondto.inventory.InventoryReservedEvent;
import com.deepana.saga.commondto.order.OrderCreatedEvent;
import com.deepana.saga.commondto.order.OrderCancelRequestedEvent;
import com.deepana.saga.commondto.order.OrderCancelledEvent;
import com.deepana.saga.commondto.order.OrderConfirmedEvent;
import com.deepana.saga.commondto.order.CancelOrderCommand;
import com.deepana.saga.commondto.payment.PaymentFailedEvent;
import com.deepana.saga.commondto.payment.PaymentSuccessEvent;
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
    private final SagaCommandProducer sagaCommandProducer;
    private final ObjectMapper objectMapper;

    // ---------------- ORDER ----------------

    @KafkaListener(
            topics = "order.created",
            groupId = "saga-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderCreated(String message) {

        try {
            OrderCreatedEvent event =
                    objectMapper.readValue(message, OrderCreatedEvent.class);

            log.info("Received order {}", event.getOrderNumber());

            sagaService.handleOrderCreated(event);

        } catch (Exception e) {
            log.error("Failed to process order.created", e);
            throw new RuntimeException(e);
        }
    }

    // ---------------- INVENTORY ----------------

    @KafkaListener(
            topics = "inventory.reserved",
            groupId = "saga-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onInventoryReserved(String message) {

        try {
            InventoryReservedEvent event =
                    objectMapper.readValue(message, InventoryReservedEvent.class);

            sagaService.handleInventoryReserved(event);

        } catch (Exception e) {
            log.error("Failed to process inventory.reserved", e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(
            topics = "inventory.failed",
            groupId = "saga-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onInventoryFailed(String message) {

        try {
            InventoryFailedEvent event =
                    objectMapper.readValue(message, InventoryFailedEvent.class);

            sagaService.handleInventoryFailed(event);

        } catch (Exception e) {
            log.error("Failed to process inventory.failed", e);
            throw new RuntimeException(e);
        }
    }

    // ---------------- PAYMENT ----------------

    @KafkaListener(
            topics = "payment.success",
            groupId = "saga-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentSuccess(String message) {

        try {
            PaymentSuccessEvent event =
                    objectMapper.readValue(message, PaymentSuccessEvent.class);

            sagaService.handlePaymentSuccess(event);

        } catch (Exception e) {
            log.error("Failed to process payment.success", e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(
            topics = "payment.failed",
            groupId = "saga-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentFailed(String message) {

        try {
            PaymentFailedEvent event =
                    objectMapper.readValue(message, PaymentFailedEvent.class);

            sagaService.handlePaymentFailed(event);

        } catch (Exception e) {
            log.error("Failed to process payment.failed", e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(
            topics = "order.cancel.requested",
            groupId = "saga-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderCancelRequested(String message) {
        try {
            OrderCancelRequestedEvent event =
                    objectMapper.readValue(message, OrderCancelRequestedEvent.class);
            CancelOrderCommand command = new CancelOrderCommand();
            command.setSagaId(event.getSagaId());
            command.setOrderId(event.getOrderId());
            command.setOrderNumber(event.getOrderNumber());
            command.setTraceId(event.getTraceId());
            command.setTimestamp(event.getTimestamp());
            command.setReason("USER_REQUESTED");
            sagaCommandProducer.sendCancelOrder(command);
        } catch (Exception e) {
            log.error("Failed to process order.cancel.requested", e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(
            topics = {"order.confirmed", "order.cancelled"},
            groupId = "saga-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderTerminalEvent(String message) {
        try {
            com.deepana.saga.commondto.base.BaseEvent event =
                    objectMapper.readValue(message, com.deepana.saga.commondto.base.BaseEvent.class);
            log.info("Received terminal order event for order {}", event.getOrderId());
            // TODO Phase 4: validate the saga pivot and complete the persisted saga from this event.
        } catch (Exception e) {
            log.error("Failed to process order.confirmed/order.cancelled", e);
            throw new RuntimeException(e);
        }
    }
}
