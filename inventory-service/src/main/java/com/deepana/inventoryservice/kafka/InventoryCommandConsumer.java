package com.deepana.inventoryservice.kafka;

import com.deepana.inventoryservice.service.InventoryService;
import com.deepana.saga.commondto.inventory.ReleaseInventoryCommand;
import com.deepana.saga.commondto.inventory.ReserveInventoryCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.slf4j.MDC;


@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryCommandConsumer {

    private final ObjectMapper objectMapper;
    private final InventoryService inventoryService;

    @KafkaListener(
            topics = "inventory.reserve.cmd",
            groupId = "inventory-group"
    )
    public void consumeReserveCommand(String message) {

        try {

            ReserveInventoryCommand cmd =
                    objectMapper.readValue(message, ReserveInventoryCommand.class);

            MDC.put("traceId", cmd.getTraceId());

            log.info("Received inventory.reserve.cmd: {}", cmd.getOrderId());

            inventoryService.processReserve(cmd);

        } catch (Exception e) {

            log.error("Inventory processing failed", e);
            throw new RuntimeException(e);

        } finally {
            MDC.clear();
        }
    }

    @KafkaListener(
            topics = "inventory.release.cmd",
            groupId = "inventory-group"
    )
    public void consumeReleaseCommand(String message) {

        try {

            ReleaseInventoryCommand cmd =
                    objectMapper.readValue(message, ReleaseInventoryCommand.class);

            MDC.put("traceId", cmd.getTraceId());

            log.info("Received inventory.release.cmd: {}", cmd.getOrderId());

            inventoryService.processRelease(cmd);

        } catch (Exception e) {

            log.error("Inventory release failed", e);
            throw new RuntimeException(e);

        } finally {
            MDC.clear();
        }
    }

}
