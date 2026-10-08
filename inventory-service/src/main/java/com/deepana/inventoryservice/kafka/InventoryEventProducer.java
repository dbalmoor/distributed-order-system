package com.deepana.inventoryservice.kafka;

import com.deepana.saga.commondto.inventory.InventoryFailedEvent;
import com.deepana.saga.commondto.inventory.InventoryReservedEvent;
import com.deepana.saga.commondto.base.BaseEvent;
import com.deepana.inventoryservice.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventProducer {

    private final OutboxWriter outboxWriter;

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
        if (!(payload instanceof BaseEvent event)) {
            throw new IllegalArgumentException("Inventory outbox payload must be a BaseEvent");
        }
        outboxWriter.write("ORDER", topic, topic, event);
        log.info("Inventory event queued in outbox [{}] for key {}", topic, key);
    }
}
