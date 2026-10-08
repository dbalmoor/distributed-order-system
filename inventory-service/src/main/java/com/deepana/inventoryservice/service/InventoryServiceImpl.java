package com.deepana.inventoryservice.service;

import com.deepana.inventoryservice.common.logging.SagaLogger;
import com.deepana.inventoryservice.entity.Inventory;
import com.deepana.inventoryservice.entity.ProcessedInventoryEvent;
import com.deepana.inventoryservice.kafka.InventoryEventProducer;
import com.deepana.inventoryservice.repository.InventoryRepository;

import com.deepana.inventoryservice.repository.ProcessedInventoryEventRepository;
import com.deepana.saga.commondto.base.BaseEvent;
import com.deepana.saga.commondto.inventory.*;
import com.deepana.saga.commondto.order.OrderItemEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Service
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final ProcessedInventoryEventRepository processedRepo;

    private final InventoryRepository inventoryRepository;
    private final InventoryEventProducer producer;

    @Override
    @Transactional
    public void processReserve(ReserveInventoryCommand cmd) {

        Long orderId = cmd.getOrderId();

        SagaLogger.success("INVENTORY", String.valueOf(orderId), "RECEIVED_RESERVE_CMD");

        // 1️⃣ Idempotency check
        if (processedRepo.existsBySagaIdAndEventType(
                cmd.getSagaId(), "RESERVE")) {

            log.info("Reserve already processed for saga {}", cmd.getSagaId());
            return;
        }

        List<Inventory> lockedInventory = new ArrayList<>();
        for (OrderItemEvent item : cmd.getItems()) {
            Inventory inventory = inventoryRepository
                    .findByProductIdForUpdate(item.getProductId())
                    .orElse(null);
            if (inventory == null) {
                emitReserveFailure(cmd, "Product not found: " + item.getProductId());
                return;
            }
            if (inventory.getAvailableQty() < item.getQuantity()) {
                emitReserveFailure(cmd, "Insufficient stock for product " + item.getProductId());
                return;
            }
            lockedInventory.add(inventory);
        }

        for (int i = 0; i < cmd.getItems().size(); i++) {
            OrderItemEvent item = cmd.getItems().get(i);
            Inventory inventory = lockedInventory.get(i);
            inventory.setAvailableQty(inventory.getAvailableQty() - item.getQuantity());
            inventory.setReservedQty(inventory.getReservedQty() + item.getQuantity());
            inventoryRepository.save(inventory);
        }

        ProcessedInventoryEvent processed = new ProcessedInventoryEvent();
        processed.setSagaId(cmd.getSagaId());
        processed.setOrderId(orderId);
        processed.setEventType("RESERVE");
        processed.setProcessedAt(LocalDateTime.now());
        processedRepo.save(processed);

        InventoryReservedEvent successEvent = new InventoryReservedEvent();
        copyBaseFields(cmd, successEvent);
        successEvent.setTotalAmount(cmd.getTotalAmount());
        successEvent.setItems(cmd.getItems());
        producer.sendInventoryReserved(successEvent);

        SagaLogger.success("INVENTORY", String.valueOf(orderId), "RESERVED_EVENT_PUBLISHED");
    }

    private void emitReserveFailure(ReserveInventoryCommand cmd, String reason) {
            InventoryFailedEvent failedEvent = new InventoryFailedEvent();
            copyBaseFields(cmd, failedEvent);
            failedEvent.setReason(reason);
            producer.sendInventoryFailed(failedEvent);
            SagaLogger.failed("INVENTORY", String.valueOf(cmd.getOrderId()), "FAILED_EVENT_PUBLISHED");
    }


    @Override
    @Transactional
    public void processRelease(ReleaseInventoryCommand cmd) {

        // 1️⃣ Idempotency check
        if (processedRepo.existsBySagaIdAndEventType(
                cmd.getSagaId(), "RELEASE")) {

            log.info("Release already processed for saga {}", cmd.getSagaId());
            return;
        }

        SagaLogger.success(
                "INVENTORY",
                cmd.getOrderNumber(),
                "RELEASE_RECEIVED"
        );

        // 2️⃣ Restore inventory
        for (OrderItemEvent item : cmd.getItems()) {

            Inventory inventory =
                    inventoryRepository
                            .findByProductIdForUpdate(item.getProductId())
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "Inventory not found for product "
                                                    + item.getProductId()));

            inventory.setAvailableQty(
                    inventory.getAvailableQty() + item.getQuantity()
            );

            inventory.setReservedQty(
                    inventory.getReservedQty() - item.getQuantity()
            );

            inventoryRepository.save(inventory);
        }

        // 3️⃣ Mark RELEASE processed
        ProcessedInventoryEvent processed = new ProcessedInventoryEvent();
        processed.setSagaId(cmd.getSagaId());
        processed.setOrderId(cmd.getOrderId());
        processed.setEventType("RELEASE");
        processed.setProcessedAt(LocalDateTime.now());

        processedRepo.save(processed);

        SagaLogger.success(
                "INVENTORY",
                cmd.getOrderNumber(),
                "INVENTORY_RELEASED"
        );

        log.info("Inventory released for order {}", cmd.getOrderId());
    }

    private void copyBaseFields(BaseEvent source, BaseEvent target) {
        target.setSagaId(source.getSagaId());
        target.setOrderId(source.getOrderId());
        target.setTraceId(source.getTraceId());
        target.setOrderNumber(source.getOrderNumber());
        target.setTimestamp(source.getTimestamp());
    }





}
