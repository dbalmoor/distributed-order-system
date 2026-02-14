package com.deepana.inventoryservice.kafka;

import com.deepana.saga.commondto.inventory.InventoryFailedEvent;
import com.deepana.saga.commondto.inventory.InventoryReservedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final String RESERVED_TOPIC = "inventory.reserved";
    private static final String FAILED_TOPIC = "inventory.failed";

    // ================= RESERVED =================

    public void sendInventoryReserved(InventoryReservedEvent event) {

        send(RESERVED_TOPIC, String.valueOf(event.getOrderId()), event);
    }

    // ================= FAILED =================

    public void sendInventoryFailed(InventoryFailedEvent event) {

        send(FAILED_TOPIC, String.valueOf(event.getOrderId()), event);
    }

    // ================= COMMON SEND =================

    private void send(String topic, String key, Object payload) {

        try {

            String json = objectMapper.writeValueAsString(payload);

            kafkaTemplate.send(topic, key, json);

            log.info("Inventory Event Sent [{}] => {}", topic, json);

        } catch (Exception e) {

            log.error("Failed to publish event to {}", topic, e);
            throw new RuntimeException(e);
        }
    }
}
