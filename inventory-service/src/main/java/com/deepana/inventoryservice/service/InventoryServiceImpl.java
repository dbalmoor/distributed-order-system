package com.deepana.inventoryservice.service;

import com.deepana.inventoryservice.common.logging.SagaLogger;
import com.deepana.inventoryservice.entity.Inventory;
import com.deepana.inventoryservice.entity.ProcessedOrder;
import com.deepana.inventoryservice.kafka.InventoryEventProducer;
import com.deepana.inventoryservice.repository.InventoryRepository;
import com.deepana.inventoryservice.repository.ProcessedOrderRepository;

import com.deepana.saga.commondto.inventory.*;
import com.deepana.saga.commondto.order.OrderItemEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@Service
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final ProcessedOrderRepository processedRepo;
    private final InventoryRepository inventoryRepository;
    private final InventoryEventProducer producer;

    @Override
    public void processReserve(ReserveInventoryCommand cmd) {

        Long orderId = cmd.getOrderId();

        SagaLogger.success("INVENTORY", String.valueOf(orderId), "RECEIVED_RESERVE_CMD");

        try {

            // ✅ Idempotency
            if (processedRepo.existsById(orderId)) {
                log.info("Order {} already processed. Skipping.", orderId);
                return;
            }

            SagaLogger.success("INVENTORY", String.valueOf(orderId), "CHECKING_STOCK");

            for (OrderItemEvent item : cmd.getItems()) {

                Inventory inventory = inventoryRepository
                        .findByProductId(item.getProductId())
                        .orElseThrow(() ->
                                new RuntimeException("Product not found: " + item.getProductId())
                        );

                if (inventory.getAvailableQty() < item.getQuantity()) {

                    SagaLogger.failed("INVENTORY", String.valueOf(orderId),
                            "INSUFFICIENT_STOCK_PRODUCT_" + item.getProductId());

                    throw new RuntimeException("Insufficient stock");
                }

                inventory.setAvailableQty(
                        inventory.getAvailableQty() - item.getQuantity()
                );

                inventory.setReservedQty(
                        inventory.getReservedQty() + item.getQuantity()
                );

                inventoryRepository.save(inventory);

                SagaLogger.success("INVENTORY", String.valueOf(orderId),
                        "RESERVED_PRODUCT_" + item.getProductId());
            }

            // ✅ Mark as processed
            ProcessedOrder processed = new ProcessedOrder();
            processed.setOrderId(orderId);
            processed.setProcessedAt(LocalDateTime.now());

            processedRepo.save(processed);

            // ✅ Publish success event
            InventoryReservedEvent successEvent = new InventoryReservedEvent();

            // 🔹 Copy BaseEvent fields
            successEvent.setSagaId(cmd.getSagaId());
            successEvent.setOrderId(cmd.getOrderId());
            successEvent.setTraceId(cmd.getTraceId());
            successEvent.setOrderNumber(cmd.getOrderNumber());
            successEvent.setTimestamp(cmd.getTimestamp());

            // 🔹 Business fields
            successEvent.setTotalAmount(cmd.getTotalAmount());

            producer.sendInventoryReserved(successEvent);


            SagaLogger.success("INVENTORY", String.valueOf(orderId), "RESERVED_EVENT_PUBLISHED");

        } catch (Exception ex) {

            InventoryFailedEvent failedEvent = new InventoryFailedEvent();

            // 🔹 Copy BaseEvent fields
            failedEvent.setSagaId(cmd.getSagaId());
            failedEvent.setOrderId(cmd.getOrderId());
            failedEvent.setTraceId(cmd.getTraceId());
            failedEvent.setOrderNumber(cmd.getOrderNumber());
            failedEvent.setTimestamp(cmd.getTimestamp());

            // 🔹 Business
            failedEvent.setReason(ex.getMessage());

            producer.sendInventoryFailed(failedEvent);


            SagaLogger.failed("INVENTORY", String.valueOf(orderId), "FAILED_EVENT_PUBLISHED");

            log.warn("Inventory FAILED for order {} : {}", orderId, ex.getMessage());
        }
    }

}
