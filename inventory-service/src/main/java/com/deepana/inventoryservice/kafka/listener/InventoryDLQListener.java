package com.deepana.inventoryservice.kafka.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class InventoryDLQListener {

    @KafkaListener(
            topics = "inventory.dlq",
            groupId = "inventory-dlq-group"
    )
    public void listenDLQ(String message) {

        log.error("🚨 INVENTORY DLQ MESSAGE RECEIVED");
        log.error("Payload: {}", message);

        // Later you can store in DB / Elastic / File
    }
}
