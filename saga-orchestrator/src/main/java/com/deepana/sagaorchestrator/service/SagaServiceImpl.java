package com.deepana.sagaorchestrator.service;

import com.deepana.sagaorchestrator.commands.*;
import com.deepana.sagaorchestrator.dto.*;
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

    // ================= ORDER CREATED =================

    @Override
    public void handleOrderCreated(OrderCreatedEvent event) {

        try {

            MDC.put("traceId", event.getTraceId());

            log.info("Saga STARTED for {}", event.getOrderNumber());

            ReserveInventoryCommand command =
                    new ReserveInventoryCommand(
                            event.getOrderId(),
                            event.getOrderNumber(),
                            event.getTotalAmount(),
                            event.getTraceId()
                    );

            producer.sendReserveInventory(command);

            log.info("ReserveInventoryCommand sent for {}",
                    event.getOrderNumber());

        } finally {
            MDC.clear();
        }
    }

    // ================= INVENTORY RESERVED =================

    @Override
    public void handleInventoryReserved(InventoryReservedEvent event) {

        try {

            MDC.put("traceId", event.getTraceId());

            log.info("Inventory RESERVED for {}",
                    event.getOrderNumber());

            ChargePaymentCommand command =
                    new ChargePaymentCommand(
                            event.getOrderId(),
                            event.getOrderNumber(),
                            event.getAmount(),
                            event.getTraceId()
                    );

            producer.sendChargePayment(command);

            log.info("ChargePaymentCommand sent for {}",
                    event.getOrderNumber());

        } finally {
            MDC.clear();
        }
    }

    // ================= INVENTORY FAILED =================

    @Override
    public void handleInventoryFailed(InventoryFailedEvent event) {

        try {

            MDC.put("traceId", event.getTraceId());

            log.warn("Inventory FAILED for {}",
                    event.getOrderNumber());

            CancelOrderCommand command =
                    new CancelOrderCommand(
                            event.getOrderId(),
                            event.getOrderNumber(),
                            event.getTraceId(),
                            "INVENTORY_FAILED"
                    );

            producer.sendCancelOrder(command);

            log.info("CancelOrderCommand sent for {}",
                    event.getOrderNumber());

        } finally {
            MDC.clear();
        }
    }

    // ================= PAYMENT SUCCESS =================

    @Override
    public void handlePaymentSuccess(PaymentSuccessEvent event) {

        try {

            MDC.put("traceId", event.getTraceId());

            log.info("Payment SUCCESS for {}",
                    event.getOrderNumber());

            ConfirmOrderCommand command =
                    new ConfirmOrderCommand(
                            event.getOrderId(),
                            event.getOrderNumber(),
                            event.getTraceId()
                    );

            producer.sendConfirmOrder(command);

            log.info("ConfirmOrderCommand sent for {}",
                    event.getOrderNumber());

        } finally {
            MDC.clear();
        }
    }

    // ================= PAYMENT FAILED =================

    @Override
    public void handlePaymentFailed(PaymentFailedEvent event) {

        try {

            MDC.put("traceId", event.getTraceId());

            log.warn("Payment FAILED for {}",
                    event.getOrderNumber());

            // 1️⃣ Release Inventory
            ReleaseInventoryCommand releaseCmd =
                    new ReleaseInventoryCommand(
                            event.getOrderId(),
                            event.getOrderNumber(),
                            event.getTraceId()
                    );

            producer.sendReleaseInventory(releaseCmd);

            log.info("ReleaseInventoryCommand sent for {}",
                    event.getOrderNumber());


            // 2️⃣ Cancel Order
            CancelOrderCommand cancelCmd =
                    new CancelOrderCommand(
                            event.getOrderId(),
                            event.getOrderNumber(),
                            event.getTraceId(),
                            "PAYMENT_FAILED"
                    );

            producer.sendCancelOrder(cancelCmd);

            log.info("CancelOrderCommand sent for {}",
                    event.getOrderNumber());

        } finally {
            MDC.clear();
        }
    }
}
