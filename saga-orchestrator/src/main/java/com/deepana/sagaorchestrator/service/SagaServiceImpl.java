package com.deepana.sagaorchestrator.service;

import com.deepana.saga.commondto.base.BaseEvent;
import com.deepana.saga.commondto.inventory.InventoryFailedEvent;
import com.deepana.saga.commondto.inventory.InventoryReservedEvent;
import com.deepana.saga.commondto.inventory.ReserveInventoryCommand;
import com.deepana.saga.commondto.order.CancelOrderCommand;
import com.deepana.saga.commondto.order.ConfirmOrderCommand;
import com.deepana.saga.commondto.order.OrderCancelRequestedEvent;
import com.deepana.saga.commondto.order.OrderCancelledEvent;
import com.deepana.saga.commondto.order.OrderConfirmedEvent;
import com.deepana.saga.commondto.order.OrderCreatedEvent;
import com.deepana.saga.commondto.payment.ChargePaymentCommand;
import com.deepana.saga.commondto.payment.PaymentFailedEvent;
import com.deepana.saga.commondto.payment.PaymentSuccessEvent;
import com.deepana.sagaorchestrator.entity.SagaSnapshot;
import com.deepana.sagaorchestrator.entity.SagaStatus;
import com.deepana.sagaorchestrator.entity.SagaStep;
import com.deepana.sagaorchestrator.entity.SagaTransition;
import com.deepana.sagaorchestrator.kafka.SagaCommandProducer;
import com.deepana.sagaorchestrator.repository.SagaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SagaServiceImpl implements SagaService {

    private static final String CONSUMER_PREFIX = "saga-orchestrator:";

    private final SagaRepository sagaRepository;
    private final SagaStateMachine stateMachine;
    private final SagaCommandProducer producer;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Value("${saga.step-deadline:PT30M}")
    private Duration stepDeadline;

    @Override
    @Transactional
    public void handleOrderCreated(OrderCreatedEvent event, String messageId) {
        withTrace(event, () -> {
            if (!markMessageProcessed("order.created", messageId)) {
                return;
            }
            requireOrderId(event);
            String sagaId = UUID.randomUUID().toString();
            if (!sagaRepository.insertSagaIfAbsent(
                    sagaId, event.getOrderId(), SagaStatus.ACTIVE, SagaStep.RESERVE_INVENTORY,
                    stepDeadline.toMillis())) {
                duplicate("order.created", messageId);
                return;
            }
            SagaSnapshot saga = sagaRepository.findBySagaIdForUpdate(sagaId)
                    .orElseThrow(() -> new IllegalStateException("Created saga row could not be reloaded"));
            sagaRepository.appendStepLog(sagaId, SagaStep.RESERVE_INVENTORY,
                    "order.created", null, SagaStatus.ACTIVE, serialize(event));

            ReserveInventoryCommand command = new ReserveInventoryCommand();
            copyBaseFields(event, command, sagaId);
            command.setTotalAmount(event.getTotalAmount());
            command.setItems(event.getItems());
            producer.sendReserveInventory(command);
            log.info("Started saga {} for order {}", saga.sagaId(), saga.orderId());
        });
    }

    @Override
    @Transactional
    public void handleInventoryReserved(InventoryReservedEvent event, String messageId) {
        withTrace(event, () -> processEvent("inventory.reserved", event, messageId,
                SagaTrigger.INVENTORY_RESERVED, false, (saga, transition) -> {
                    sagaRepository.updateState(saga.sagaId(), transition.status(), transition.step(),
                            stepDeadline.toMillis());
                    sagaRepository.appendStepLog(saga.sagaId(), transition.step(), "inventory.reserved",
                            saga.status(), transition.status(), serialize(event));

                    ChargePaymentCommand command = new ChargePaymentCommand();
                    copyBaseFields(event, command, saga.sagaId());
                    command.setTotalAmount(event.getTotalAmount());
                    command.setItems(event.getItems());
                    producer.sendChargePayment(command);
                }));
    }

    @Override
    @Transactional
    public void handleInventoryFailed(InventoryFailedEvent event, String messageId) {
        withTrace(event, () -> processEvent("inventory.failed", event, messageId,
                SagaTrigger.INVENTORY_FAILED, false, (saga, transition) -> {
                    sagaRepository.updateState(saga.sagaId(), transition.status(), transition.step(),
                            stepDeadline.toMillis());
                    sagaRepository.appendStepLog(saga.sagaId(), transition.step(), "inventory.failed",
                            saga.status(), transition.status(), serialize(event));

                    CancelOrderCommand command = cancelCommand(event, saga.sagaId(), "INVENTORY_FAILED");
                    producer.sendCancelOrder(command);
                }));
    }

    @Override
    @Transactional
    public void handlePaymentSuccess(PaymentSuccessEvent event, String messageId) {
        withTrace(event, () -> processEvent("payment.success", event, messageId,
                SagaTrigger.PAYMENT_SUCCESS, false, (saga, transition) -> {
                    sagaRepository.updateState(saga.sagaId(), transition.status(), transition.step(),
                            stepDeadline.toMillis());
                    sagaRepository.appendStepLog(saga.sagaId(), transition.step(), "payment.success",
                            saga.status(), transition.status(), serialize(event));

                    ConfirmOrderCommand command = new ConfirmOrderCommand();
                    copyBaseFields(event, command, saga.sagaId());
                    producer.sendConfirmOrder(command);
                }));
    }

    @Override
    @Transactional
    public void handlePaymentFailed(PaymentFailedEvent event, String messageId) {
        withTrace(event, () -> processEvent("payment.failed", event, messageId,
                SagaTrigger.PAYMENT_FAILED, false, (saga, transition) -> {
                    sagaRepository.updateState(saga.sagaId(), transition.status(), transition.step(),
                            stepDeadline.toMillis());
                    sagaRepository.appendStepLog(saga.sagaId(), transition.step(), "payment.failed",
                            saga.status(), transition.status(), serialize(event));

                    // TODO(Phase 5): release reserved inventory before cancelling the order.
                    producer.sendCancelOrder(cancelCommand(event, saga.sagaId(), "PAYMENT_FAILED"));
                }));
    }

    @Override
    @Transactional
    public void handleCancelRequested(OrderCancelRequestedEvent event, String messageId) {
        withTrace(event, () -> processEvent("order.cancel.requested", event, messageId,
                SagaTrigger.CANCEL_REQUESTED, true, (saga, transition) -> {
                    sagaRepository.updateState(saga.sagaId(), transition.status(), transition.step(),
                            stepDeadline.toMillis());
                    sagaRepository.appendStepLog(saga.sagaId(), transition.step(),
                            "order.cancel.requested", saga.status(), transition.status(), serialize(event));
                    producer.sendCancelOrder(cancelCommand(event, saga.sagaId(), "USER_REQUESTED"));
                }));
    }

    @Override
    @Transactional
    public void handleOrderConfirmed(OrderConfirmedEvent event, String messageId) {
        withTrace(event, () -> processEvent("order.confirmed", event, messageId,
                SagaTrigger.ORDER_CONFIRMED, false, (saga, transition) -> {
                    sagaRepository.updateState(saga.sagaId(), transition.status(), transition.step(), 0);
                    sagaRepository.appendStepLog(saga.sagaId(), transition.step(), "order.confirmed",
                            saga.status(), transition.status(), serialize(event));
                }));
    }

    @Override
    @Transactional
    public void handleOrderCancelled(OrderCancelledEvent event, String messageId) {
        withTrace(event, () -> processEvent("order.cancelled", event, messageId,
                SagaTrigger.ORDER_CANCELLED, false, (saga, transition) -> {
                    sagaRepository.updateState(saga.sagaId(), transition.status(), transition.step(), 0);
                    sagaRepository.appendStepLog(saga.sagaId(), transition.step(), "order.cancelled",
                            saga.status(), transition.status(), serialize(event));
                }));
    }

    private void processEvent(String eventType, BaseEvent event, String messageId,
                              SagaTrigger trigger, boolean locateByOrderId, TransitionAction action) {
        if (!markMessageProcessed(eventType, messageId)) {
            return;
        }
        if (locateByOrderId && event.getOrderId() != null) {
            // Cancellation requests from REST do not have a sagaId until the orchestrator creates it.
        } else if (event.getSagaId() == null || event.getSagaId().isBlank()) {
            invalid(eventType, "missing sagaId");
            return;
        }

        Optional<SagaSnapshot> found = locateByOrderId
                ? sagaRepository.findByOrderIdForUpdate(event.getOrderId())
                : sagaRepository.findBySagaIdForUpdate(event.getSagaId());
        if (found.isEmpty()) {
            invalid(eventType, "saga not found");
            return;
        }
        SagaSnapshot saga = found.get();

        if ("payment.success".equals(eventType)
                && (saga.status() == SagaStatus.COMPENSATING || saga.status() == SagaStatus.CANCELLED)) {
            sagaRepository.appendStepLog(saga.sagaId(), saga.currentStep(), "LATE_SUCCESS",
                    saga.status(), saga.status(), serialize(event));
            log.warn("Recorded late payment.success for saga {} without issuing refund (Phase 5)", saga.sagaId());
            return;
        }

        Optional<SagaTransition> decision = stateMachine.transition(saga, trigger);
        if (decision.isEmpty()) {
            invalid(eventType, "event is out of order for " + saga.status() + "/" + saga.currentStep());
            return;
        }
        action.apply(saga, decision.get());
    }

    private boolean markMessageProcessed(String eventType, String rawMessageId) {
        UUID messageId;
        try {
            messageId = UUID.fromString(rawMessageId);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Kafka message is missing a valid messageId header", e);
        }
        if (sagaRepository.markMessageProcessed(CONSUMER_PREFIX + eventType, messageId)) {
            return true;
        }
        duplicate(eventType, rawMessageId);
        return false;
    }

    private void duplicate(String eventType, String messageId) {
        meterRegistry.counter("saga.messages.duplicates", "eventType", eventType).increment();
        log.info("Ignoring duplicate saga message {} for {}", messageId, eventType);
    }

    private void invalid(String eventType, String reason) {
        meterRegistry.counter("saga.events.invalid", "eventType", eventType).increment();
        log.warn("Ignoring invalid saga event {}: {}", eventType, reason);
    }

    private void withTrace(BaseEvent event, Runnable action) {
        if (event.getTraceId() != null) {
            MDC.put("traceId", event.getTraceId());
        }
        try {
            action.run();
        } finally {
            MDC.clear();
        }
    }

    private String serialize(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize saga step payload", e);
        }
    }

    private void requireOrderId(BaseEvent event) {
        if (event.getOrderId() == null) {
            throw new IllegalArgumentException("order.created must include orderId");
        }
    }

    private CancelOrderCommand cancelCommand(BaseEvent event, String sagaId, String reason) {
        CancelOrderCommand command = new CancelOrderCommand();
        copyBaseFields(event, command, sagaId);
        command.setReason(reason);
        return command;
    }

    private void copyBaseFields(BaseEvent source, BaseEvent target, String sagaId) {
        target.setSagaId(sagaId);
        target.setOrderId(source.getOrderId());
        target.setOrderNumber(source.getOrderNumber());
        target.setTraceId(source.getTraceId());
        target.setTimestamp(source.getTimestamp());
    }

    @FunctionalInterface
    private interface TransitionAction {
        void apply(SagaSnapshot current, SagaTransition transition);
    }
}
