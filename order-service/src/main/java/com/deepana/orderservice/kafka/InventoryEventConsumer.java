package com.deepana.orderservice.kafka;

import com.deepana.orderservice.service.OrderService;
import com.deepana.saga.commondto.inventory.InventoryFailedEvent;
import com.deepana.saga.commondto.inventory.InventoryReservedEvent;

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
public class InventoryEventConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    // ================= RESERVED =================

    @KafkaListener(
            topics = "inventory.reserved",
            groupId = "order-group"
    )
    public void handleReserved(String message) throws JsonProcessingException {

        try {
            InventoryReservedEvent event = objectMapper.readValue(message, InventoryReservedEvent.class);

            MDC.put("traceId", event.getTraceId());

            log.info("Received inventory.reserved: {}", event);

            orderService.handleInventoryReserved(event);

        } catch (Exception e) {

            log.error("Failed to process inventory.reserved", e);
            throw e;

        } finally {
            MDC.clear();
        }
    }

    // ================= FAILED =================

    @KafkaListener(
            topics = "inventory.failed",
            groupId = "order-group"
    )
    public void handleFailed(String message) throws JsonProcessingException {

        try {
            InventoryFailedEvent event = objectMapper.readValue(message, InventoryFailedEvent.class);

            MDC.put("traceId", event.getTraceId());

            log.info("Received inventory.failed: {}", event);

            orderService.handleInventoryFailed(event);

        } catch (Exception e) {

            log.error("Failed to process inventory.failed", e);
            throw e;

        } finally {
            MDC.clear();
        }
    }
}
