package com.deepana.inventoryservice.service;

import com.deepana.inventoryservice.common.logging.SagaLogger;
import com.deepana.inventoryservice.entity.Inventory;
import com.deepana.inventoryservice.kafka.InventoryEventProducer;
import com.deepana.inventoryservice.repository.InventoryRepository;

import com.deepana.inventoryservice.repository.ProcessedInventoryEventRepository;
import com.deepana.saga.commondto.base.BaseEvent;
import com.deepana.saga.commondto.inventory.*;
import com.deepana.saga.commondto.order.OrderItemEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
@Service
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final ProcessedInventoryEventRepository processedRepo;

    private final InventoryRepository inventoryRepository;
    private final InventoryEventProducer producer;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void processReserve(ReserveInventoryCommand cmd) {

        Long orderId = cmd.getOrderId();

        SagaLogger.success("INVENTORY", String.valueOf(orderId), "RECEIVED_RESERVE_CMD");

        lockSaga(cmd.getSagaId());

        if (processedRepo.existsBySagaIdAndEventType(cmd.getSagaId(), "RELEASED")) {
            emitReserveFailure(cmd, "Reservation was already released or cancelled");
            return;
        }

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

        if (processedRepo.insertIfAbsent(cmd.getSagaId(), orderId, "RESERVE") != 1) {
            throw new IllegalStateException("Reservation marker already exists for saga " + cmd.getSagaId());
        }

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

        lockSaga(cmd.getSagaId());
        if (processedRepo.insertIfAbsent(cmd.getSagaId(), cmd.getOrderId(), "RELEASED") == 0) {
            log.info("Release already processed for saga {}", cmd.getSagaId());
            emitReleased(cmd);
            return;
        }

        SagaLogger.success(
                "INVENTORY",
                cmd.getOrderNumber(),
                "RELEASE_RECEIVED"
        );

        // A marker without a reservation closes the release-before-reserve race.
        if (processedRepo.existsBySagaIdAndEventType(cmd.getSagaId(), "RESERVE")) {
            if (cmd.getItems() == null) {
                throw new IllegalArgumentException("Release command must include reservation items");
            }
            for (OrderItemEvent item : cmd.getItems()) {
                int updated = inventoryRepository.releaseReservation(item.getProductId(), item.getQuantity());
                if (updated != 1) {
                    throw new IllegalStateException(
                            "Could not release reserved stock for product " + item.getProductId());
                }
            }
        }

        SagaLogger.success(
                "INVENTORY",
                cmd.getOrderNumber(),
                "INVENTORY_RELEASED"
        );

        emitReleased(cmd);
        log.info("Inventory released for order {}", cmd.getOrderId());
    }

    private void emitReleased(ReleaseInventoryCommand cmd) {
        InventoryReleasedEvent event = new InventoryReleasedEvent();
        copyBaseFields(cmd, event);
        event.setItems(cmd.getItems());
        producer.sendInventoryReleased(event);
    }

    private void lockSaga(String sagaId) {
        if (sagaId == null || sagaId.isBlank()) {
            throw new IllegalArgumentException("Inventory command must include a sagaId");
        }
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                (org.springframework.jdbc.core.RowCallbackHandler) resultSet -> { },
                sagaId);
    }

    private void copyBaseFields(BaseEvent source, BaseEvent target) {
        target.setSagaId(source.getSagaId());
        target.setOrderId(source.getOrderId());
        target.setTraceId(source.getTraceId());
        target.setOrderNumber(source.getOrderNumber());
        target.setTimestamp(source.getTimestamp());
    }





}
