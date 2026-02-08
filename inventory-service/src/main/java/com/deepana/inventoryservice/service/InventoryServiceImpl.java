package com.deepana.inventoryservice.service;

import com.deepana.inventoryservice.common.logging.SagaLogger;
import com.deepana.inventoryservice.entity.Inventory;
import com.deepana.inventoryservice.events.InventoryFailedEvent;
import com.deepana.inventoryservice.events.InventoryReservedEvent;
import com.deepana.inventoryservice.events.OrderCreatedEvent;
import com.deepana.inventoryservice.events.OrderItemEvent;
import com.deepana.inventoryservice.kafka.InventoryEventProducer;
import com.deepana.inventoryservice.repository.InventoryRepository;
import com.deepana.inventoryservice.repository.ProcessedOrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.slf4j.MDC;

@RequiredArgsConstructor
@Service
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final ProcessedOrderRepository processedRepo;
    private final InventoryRepository inventoryRepository;
    private final InventoryEventProducer producer;

    @Override
    public void processOrder(OrderCreatedEvent event) throws JsonProcessingException {

        String orderNo = event.getOrderNumber();

        SagaLogger.success("INVENTORY", orderNo, "RECEIVED_ORDER");

        try {

            SagaLogger.success("INVENTORY", orderNo, "CHECKING_STOCK");

            for (OrderItemEvent item : event.getItems()) {

                Inventory inventory = inventoryRepository
                        .findByProductId(item.getProductId())
                        .orElseThrow(() ->
                                new RuntimeException("Product not found: " + item.getProductId())
                        );

                // ✅ Already reserved?
                if (processedRepo.existsById(event.getOrderId())) {
                    log.info("Order {} already reserved. Skipping.", event.getOrderNumber());
                    return;
                }

                SagaLogger.success("INVENTORY", orderNo,
                        "FOUND_PRODUCT_" + item.getProductId());

                if (inventory.getAvailableQty() < item.getQuantity()) {

                    SagaLogger.failed("INVENTORY", orderNo,
                            "INSUFFICIENT_STOCK_PRODUCT_" + item.getProductId());

                    return;
                }

                inventory.setAvailableQty(
                        inventory.getAvailableQty() - item.getQuantity()
                );

                inventory.setReservedQty(
                        inventory.getReservedQty() + item.getQuantity()
                );

                inventoryRepository.save(inventory);

                SagaLogger.success("INVENTORY", orderNo,
                        "RESERVED_PRODUCT_" + item.getProductId());
            }

            InventoryReservedEvent successEvent = new InventoryReservedEvent();
            successEvent.setOrderId(event.getOrderId());
            successEvent.setOrderNumber(orderNo);
            successEvent.setAmount(event.getTotalAmount());
            successEvent.setTraceId(event.getTraceId());

            producer.sendInventoryReserved(successEvent);

            SagaLogger.success("INVENTORY", orderNo, "EVENT_PUBLISHED");

        } catch (Exception ex) {

            InventoryFailedEvent failedEvent = new InventoryFailedEvent();
            failedEvent.setOrderId(event.getOrderId());
            failedEvent.setOrderNumber(orderNo);
            failedEvent.setReason(ex.getMessage());
            failedEvent.setTraceId(event.getTraceId());

            producer.sendInventoryFailed(failedEvent);

            SagaLogger.failed("INVENTORY", orderNo, "EVENT_PUBLISHED_FAILED");

            log.warn("Inventory FAILED for order {} : {}",
                    orderNo, ex.getMessage());

            // ✅ No crash
        }
    }

}
