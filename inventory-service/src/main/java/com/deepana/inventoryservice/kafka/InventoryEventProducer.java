package com.deepana.inventoryservice.kafka;

import com.deepana.inventoryservice.events.InventoryFailedEvent;
import com.deepana.inventoryservice.events.InventoryReservedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.MDC;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final String RESERVED_TOPIC = "inventory.reserved";
    private static final String FAILED_TOPIC = "inventory.failed";

    public void sendInventoryReserved(InventoryReservedEvent event) {

        try {
            String json = objectMapper.writeValueAsString(event);

            kafkaTemplate.send(RESERVED_TOPIC, event.getOrderNumber(), json);

            log.info("Inventory reserved event sent: {}", json);

        } catch (Exception e) {
            log.error("Failed to send inventory reserved event", e);
        }
    }

    public void sendInventoryFailed(InventoryFailedEvent event) throws JsonProcessingException {

        try {
            String json = objectMapper.writeValueAsString(event);

            kafkaTemplate.send(FAILED_TOPIC, event.getOrderNumber(), json);

            log.info("Inventory failed event sent: {}", json);

        } catch (Exception e) {
            log.error("Failed to send inventory failed event", e);
            throw e;
        }
    }
}
