package com.deepana.orderservice.kafka.listener;

import com.deepana.orderservice.common.logging.SagaLogger;
import com.deepana.orderservice.entity.Order;
import com.deepana.orderservice.entity.OrderStatus;
import com.deepana.orderservice.events.InventoryFailedEvent;
import com.deepana.orderservice.events.InventoryReservedEvent;
import com.deepana.orderservice.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class InventoryEventListener {

    private final ObjectMapper objectMapper;
    private final OrderRepository orderRepository;


    @KafkaListener(
            topics = "inventory.reserved",
            groupId = "order-group"
    )
    public void handleInventoryReserved(String message) {

        try {
            log.info("Received inventory.reserved: {}", message);

            InventoryReservedEvent event =
                    objectMapper.readValue(message, InventoryReservedEvent.class);

            Order order = orderRepository
                    .findById(event.getOrderId())
                    .orElseThrow(() ->
                            new RuntimeException("Order not found: " + event.getOrderId())
                    );

            order.setStatus(OrderStatus.INVENTORY_RESERVED);

            orderRepository.save(order);

            log.info("Order {} CONFIRMED", event.getOrderNumber());

            SagaLogger.success(
                    "ORDER",
                    event.getOrderNumber(),
                    "INVENTORY_RESERVED"
            );


        } catch (Exception e) {
            log.error("Failed to process inventory.reserved", e);
        }
    }


    @KafkaListener(
            topics = "inventory.failed",
            groupId = "order-group"
    )
    public void handleInventoryFailed(String message) {

        try {
            log.info("Received inventory.failed: {}", message);

            InventoryFailedEvent event =
                    objectMapper.readValue(message, InventoryFailedEvent.class);

            Order order = orderRepository
                    .findById(event.getOrderId())
                    .orElseThrow(() ->
                            new RuntimeException("Order not found: " + event.getOrderId())
                    );

            order.setStatus(OrderStatus.FAILED);

            orderRepository.save(order);

            log.info("Order {} FAILED: {}",
                    event.getOrderNumber(),
                    event.getReason());

            SagaLogger.failed(
                    "ORDER",
                    event.getOrderNumber(),
                    "INVENTORY_FAILED"
            );

        } catch (Exception e) {
            log.error("Failed to process inventory.failed", e);
        }
    }
}
