package com.deepana.sagaorchestrator.service;

import com.deepana.saga.commondto.base.BaseEvent;
import com.deepana.saga.commondto.inventory.*;
import com.deepana.saga.commondto.order.*;
import com.deepana.saga.commondto.payment.*;
import com.deepana.sagaorchestrator.kafka.SagaCommandProducer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SagaServiceImpl implements SagaService {

    private final SagaCommandProducer producer;

    // =========================================================
    // ORDER CREATED → RESERVE INVENTORY
    // =========================================================

    @Override
    public void handleOrderCreated(OrderCreatedEvent event) {

        try {
            MDC.put("traceId", event.getTraceId());

            log.info("Saga STARTED for {}", event.getOrderNumber());

            ReserveInventoryCommand command = new ReserveInventoryCommand();

            copyBaseFields(event, command);
            command.setTotalAmount(event.getTotalAmount());
            command.setItems(event.getItems());

            producer.sendReserveInventory(command);

            log.info("ReserveInventoryCommand sent for {}",
                    event.getOrderNumber());

        } finally {
            MDC.clear();
        }
    }

    // =========================================================
    // INVENTORY RESERVED → CHARGE PAYMENT
    // =========================================================

    @Override
    public void handleInventoryReserved(InventoryReservedEvent event) {

        try {
            MDC.put("traceId", event.getTraceId());

            log.info("Inventory RESERVED for {}",
                    event.getOrderNumber());

            ChargePaymentCommand command = new ChargePaymentCommand();

            copyBaseFields(event, command);
            command.setTotalAmount(event.getTotalAmount());

            producer.sendChargePayment(command);

            log.info("ChargePaymentCommand sent for {}",
                    event.getOrderNumber());

        } finally {
            MDC.clear();
        }
    }

    // =========================================================
    // INVENTORY FAILED → CANCEL ORDER
    // =========================================================

    @Override
    public void handleInventoryFailed(InventoryFailedEvent event) {

        try {
            MDC.put("traceId", event.getTraceId());

            log.warn("Inventory FAILED for {}",
                    event.getOrderNumber());

            CancelOrderCommand command = new CancelOrderCommand();

            copyBaseFields(event, command);
            command.setReason("INVENTORY_FAILED");

            producer.sendCancelOrder(command);

            log.info("CancelOrderCommand sent for {}",
                    event.getOrderNumber());

        } finally {
            MDC.clear();
        }
    }

    // =========================================================
    // PAYMENT SUCCESS → CONFIRM ORDER
    // =========================================================

    @Override
    public void handlePaymentSuccess(PaymentSuccessEvent event) {

        try {
            MDC.put("traceId", event.getTraceId());

            log.info("Payment SUCCESS for {}",
                    event.getOrderNumber());

            ConfirmOrderCommand command = new ConfirmOrderCommand();

            copyBaseFields(event, command);

            producer.sendConfirmOrder(command);

            log.info("ConfirmOrderCommand sent for {}",
                    event.getOrderNumber());

        } finally {
            MDC.clear();
        }
    }

    // =========================================================
    // PAYMENT FAILED → RELEASE INVENTORY + CANCEL ORDER
    // =========================================================

    @Override
    public void handlePaymentFailed(PaymentFailedEvent event) {

        try {
            MDC.put("traceId", event.getTraceId());

            log.warn("Payment FAILED for {}",
                    event.getOrderNumber());

            // 1️⃣ Release Inventory
            ReleaseInventoryCommand releaseCmd = new ReleaseInventoryCommand();
            copyBaseFields(event, releaseCmd);

            producer.sendReleaseInventory(releaseCmd);

            log.info("ReleaseInventoryCommand sent for {}",
                    event.getOrderNumber());

            // 2️⃣ Cancel Order
            CancelOrderCommand cancelCmd = new CancelOrderCommand();
            copyBaseFields(event, cancelCmd);
            cancelCmd.setReason("PAYMENT_FAILED");

            producer.sendCancelOrder(cancelCmd);

            log.info("CancelOrderCommand sent for {}",
                    event.getOrderNumber());

        } finally {
            MDC.clear();
        }
    }

    // =========================================================
    // COMMON HELPER
    // =========================================================

    private void copyBaseFields(BaseEvent source, BaseEvent target) {

        target.setSagaId(source.getSagaId());
        target.setOrderId(source.getOrderId());
        target.setOrderNumber(source.getOrderNumber());
        target.setTraceId(source.getTraceId());
        target.setTimestamp(source.getTimestamp());
    }
}
