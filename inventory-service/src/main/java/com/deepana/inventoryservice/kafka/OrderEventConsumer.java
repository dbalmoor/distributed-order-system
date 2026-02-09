package com.deepana.inventoryservice.kafka;

import com.deepana.inventoryservice.dto.events.OrderCreatedEvent;
import com.deepana.inventoryservice.service.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.slf4j.MDC;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderEventConsumer {

    private final ObjectMapper objectMapper;
    private final InventoryService inventoryService;

    @KafkaListener(
            topics = "order.created",
            groupId = "inventory-group"
    )
    public void consumeOrderCreated(String message) {

        try {

            OrderCreatedEvent event =
                    objectMapper.readValue(message, OrderCreatedEvent.class);

            MDC.put("traceId", event.getTraceId());

            log.info("Received order: {}", event.getOrderNumber());

            inventoryService.processOrder(event);


        } catch (Exception e) {

            log.error("Inventory processing failed", e);

            // VERY IMPORTANT
            throw new RuntimeException(e);
        } finally {

            MDC.clear(); // important
        }
    }

}
