package com.deepana.sagaorchestrator.kafka;


import com.deepana.saga.commondto.inventory.InventoryFailedEvent;
import com.deepana.saga.commondto.inventory.InventoryReservedEvent;
import com.deepana.saga.commondto.inventory.InventoryReleasedEvent;
import com.deepana.saga.commondto.order.OrderCreatedEvent;
import com.deepana.saga.commondto.order.OrderCancelRequestedEvent;
import com.deepana.saga.commondto.order.OrderCancelledEvent;
import com.deepana.saga.commondto.order.OrderConfirmedEvent;
import com.deepana.saga.commondto.payment.PaymentFailedEvent;
import com.deepana.saga.commondto.payment.PaymentSuccessEvent;
import com.deepana.saga.commondto.payment.PaymentRefundedEvent;
import com.deepana.sagaorchestrator.service.SagaService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class SagaEventConsumer {

    private final SagaService sagaService;
    private final ObjectMapper objectMapper;

    // ---------------- ORDER ----------------

    @KafkaListener(
            topics = "order.created",
            groupId = "saga-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderCreated(ConsumerRecord<String, String> record) throws JsonProcessingException {
        OrderCreatedEvent event = objectMapper.readValue(record.value(), OrderCreatedEvent.class);
        sagaService.handleOrderCreated(event, messageId(record));
    }

    // ---------------- INVENTORY ----------------

    @KafkaListener(
            topics = "inventory.reserved",
            groupId = "saga-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onInventoryReserved(ConsumerRecord<String, String> record) throws JsonProcessingException {
        InventoryReservedEvent event =
                objectMapper.readValue(record.value(), InventoryReservedEvent.class);
        sagaService.handleInventoryReserved(event, messageId(record));
    }

    @KafkaListener(
            topics = "inventory.failed",
            groupId = "saga-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onInventoryFailed(ConsumerRecord<String, String> record) throws JsonProcessingException {
        InventoryFailedEvent event =
                objectMapper.readValue(record.value(), InventoryFailedEvent.class);
        sagaService.handleInventoryFailed(event, messageId(record));
    }

    @KafkaListener(
            topics = "inventory.released",
            groupId = "saga-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onInventoryReleased(ConsumerRecord<String, String> record) throws JsonProcessingException {
        InventoryReleasedEvent event =
                objectMapper.readValue(record.value(), InventoryReleasedEvent.class);
        sagaService.handleInventoryReleased(event, messageId(record));
    }

    // ---------------- PAYMENT ----------------

    @KafkaListener(
            topics = "payment.success",
            groupId = "saga-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentSuccess(ConsumerRecord<String, String> record) throws JsonProcessingException {
        PaymentSuccessEvent event =
                objectMapper.readValue(record.value(), PaymentSuccessEvent.class);
        sagaService.handlePaymentSuccess(event, messageId(record));
    }

    @KafkaListener(
            topics = "payment.failed",
            groupId = "saga-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentFailed(ConsumerRecord<String, String> record) throws JsonProcessingException {
        PaymentFailedEvent event =
                objectMapper.readValue(record.value(), PaymentFailedEvent.class);
        sagaService.handlePaymentFailed(event, messageId(record));
    }

    @KafkaListener(
            topics = "payment.refunded",
            groupId = "saga-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentRefunded(ConsumerRecord<String, String> record) throws JsonProcessingException {
        PaymentRefundedEvent event =
                objectMapper.readValue(record.value(), PaymentRefundedEvent.class);
        sagaService.handlePaymentRefunded(event, messageId(record));
    }

    @KafkaListener(
            topics = "order.cancel.requested",
            groupId = "saga-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderCancelRequested(ConsumerRecord<String, String> record) throws JsonProcessingException {
        OrderCancelRequestedEvent event =
                objectMapper.readValue(record.value(), OrderCancelRequestedEvent.class);
        sagaService.handleCancelRequested(event, messageId(record));
    }

    @KafkaListener(
            topics = {"order.confirmed", "order.cancelled"},
            groupId = "saga-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderTerminalEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        String messageId = messageId(record);
        if ("order.confirmed".equals(record.topic())) {
            OrderConfirmedEvent event = objectMapper.readValue(record.value(), OrderConfirmedEvent.class);
            sagaService.handleOrderConfirmed(event, messageId);
        } else {
            OrderCancelledEvent event = objectMapper.readValue(record.value(), OrderCancelledEvent.class);
            sagaService.handleOrderCancelled(event, messageId);
        }
    }

    private String messageId(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader("messageId");
        if (header == null || header.value() == null) {
            throw new IllegalArgumentException(
                    "Kafka message on " + record.topic() + " is missing messageId header");
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
