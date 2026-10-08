package com.deepana.paymentservice.service;

import com.deepana.paymentservice.common.logging.SagaLogger;
import com.deepana.paymentservice.config.PaymentProperties;
import com.deepana.paymentservice.entity.Payment;
import com.deepana.paymentservice.entity.PaymentType;
import com.deepana.paymentservice.kafka.PaymentEventProducer;
import com.deepana.paymentservice.repository.PaymentRepository;
import com.deepana.saga.commondto.base.BaseEvent;
import com.deepana.saga.commondto.payment.ChargePaymentCommand;
import com.deepana.saga.commondto.payment.PaymentFailedEvent;
import com.deepana.saga.commondto.payment.PaymentRefundedEvent;
import com.deepana.saga.commondto.payment.RefundPaymentCommand;
import com.deepana.saga.commondto.payment.PaymentSuccessEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private static final String FAILURE_REASON = "Payment declined by configured amount rule";

    private final PaymentRepository repository;
    private final PaymentEventProducer producer;
    private final PaymentProperties paymentProperties;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void processPayment(ChargePaymentCommand cmd) {
        try {
            MDC.put("traceId", cmd.getTraceId());

            if (!isUuid(cmd.getSagaId())) {
                throw new IllegalArgumentException("Payment charge command must include a sagaId UUID");
            }
            lockSaga(cmd.getSagaId());

            if (repository.findBySagaIdAndType(cmd.getSagaId(), PaymentType.REFUND).isPresent()) {
                publishFailure(cmd, "Charge rejected because a refund marker already exists");
                return;
            }

            SagaLogger.success("PAYMENT", cmd.getOrderNumber(), "PAYMENT_STARTED");
            log.info("Payment started for order {} | Amount={}",
                    cmd.getOrderNumber(), cmd.getTotalAmount());

            boolean shouldFail = paymentProperties.getFailAmountThreshold() != null
                    && cmd.getTotalAmount().compareTo(paymentProperties.getFailAmountThreshold()) >= 0;
            String status = shouldFail ? "FAILED" : "SUCCESS";

            int inserted = repository.insertIfAbsent(
                    cmd.getSagaId(),
                    PaymentType.CHARGE.name(),
                    cmd.getOrderId(),
                    cmd.getOrderNumber(),
                    cmd.getTotalAmount(),
                    status,
                    LocalDateTime.now());

            Payment payment = repository.findBySagaIdAndType(cmd.getSagaId(), PaymentType.CHARGE)
                    .orElseThrow(() -> new IllegalStateException(
                            "Payment row not found after idempotent insert for saga " + cmd.getSagaId()));

            if (inserted == 0) {
                log.info("Charge already processed for saga {}; re-emitting stored {} outcome",
                        cmd.getSagaId(), payment.getStatus());
            }

            publishStoredOutcome(payment, cmd);
        } finally {
            MDC.clear();
        }
    }

    private void publishStoredOutcome(Payment payment, ChargePaymentCommand cmd) {
        if ("SUCCESS".equals(payment.getStatus())) {
            PaymentSuccessEvent event = new PaymentSuccessEvent();
            copyBaseFields(cmd, event);
            event.setOrderId(payment.getOrderId());
            event.setOrderNumber(payment.getOrderNumber());
            event.setTimestamp(Instant.now());
            event.setTotalAmount(payment.getAmount());
            event.setItems(cmd.getItems());
            producer.sendSuccess(event);

            SagaLogger.success("PAYMENT", payment.getOrderNumber(), "PAYMENT_SUCCESS");
            log.info("Payment SUCCESS for {} | Amount={}",
                    payment.getOrderNumber(), payment.getAmount());
            return;
        }

        if ("FAILED".equals(payment.getStatus())) {
            PaymentFailedEvent event = new PaymentFailedEvent();
            copyBaseFields(cmd, event);
            event.setOrderId(payment.getOrderId());
            event.setOrderNumber(payment.getOrderNumber());
            event.setTimestamp(Instant.now());
            event.setReason(FAILURE_REASON);
            event.setTotalAmount(payment.getAmount());
            event.setItems(cmd.getItems());
            producer.sendFailed(event);

            SagaLogger.failed("PAYMENT", payment.getOrderNumber(), "PAYMENT_FAILED");
            log.warn("Payment FAILED for {} | Amount={}",
                    payment.getOrderNumber(), payment.getAmount());
            return;
        }

        throw new IllegalStateException("Unknown stored payment status: " + payment.getStatus());
    }

    @Override
    @Transactional
    public void processRefund(RefundPaymentCommand cmd) {
        try {
            MDC.put("traceId", cmd.getTraceId());
            if (!isUuid(cmd.getSagaId())) {
                throw new IllegalArgumentException("Payment refund command must include a sagaId UUID");
            }
            lockSaga(cmd.getSagaId());

            Payment charge = repository.findBySagaIdAndType(cmd.getSagaId(), PaymentType.CHARGE)
                    .filter(payment -> "SUCCESS".equals(payment.getStatus()))
                    .orElse(null);
            BigDecimal amount = charge == null ? cmd.getTotalAmount() : charge.getAmount();
            Long orderId = charge == null ? cmd.getOrderId() : charge.getOrderId();
            String orderNumber = charge == null ? cmd.getOrderNumber() : charge.getOrderNumber();

            int inserted = repository.insertIfAbsent(
                    cmd.getSagaId(),
                    PaymentType.REFUND.name(),
                    orderId,
                    orderNumber,
                    amount,
                    "REFUNDED",
                    LocalDateTime.now());
            Payment refund = repository.findBySagaIdAndType(cmd.getSagaId(), PaymentType.REFUND)
                    .orElseThrow(() -> new IllegalStateException(
                            "Refund marker not found after idempotent insert for saga " + cmd.getSagaId()));
            if (inserted == 0) {
                log.info("Refund already processed for saga {}; re-emitting payment.refunded", cmd.getSagaId());
            } else if (charge == null) {
                log.info("Recorded refund-before-charge marker for saga {}", cmd.getSagaId());
            } else {
                log.info("Refunded successful charge for saga {}", cmd.getSagaId());
            }

            PaymentRefundedEvent event = new PaymentRefundedEvent();
            copyBaseFields(cmd, event);
            event.setOrderId(refund.getOrderId());
            event.setOrderNumber(refund.getOrderNumber());
            event.setTimestamp(Instant.now());
            event.setTotalAmount(refund.getAmount());
            event.setReason(cmd.getReason());
            producer.sendRefunded(event);
        } finally {
            MDC.clear();
        }
    }

    private void publishFailure(ChargePaymentCommand cmd, String reason) {
        PaymentFailedEvent event = new PaymentFailedEvent();
        copyBaseFields(cmd, event);
        event.setOrderId(cmd.getOrderId());
        event.setOrderNumber(cmd.getOrderNumber());
        event.setTimestamp(Instant.now());
        event.setReason(reason);
        event.setTotalAmount(cmd.getTotalAmount());
        event.setItems(cmd.getItems());
        producer.sendFailed(event);
        log.warn("Payment charge rejected for saga {}: {}", cmd.getSagaId(), reason);
    }

    private void lockSaga(String sagaId) {
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
    }

    private boolean isUuid(String sagaId) {
        if (sagaId == null) {
            return false;
        }
        try {
            return UUID.fromString(sagaId).toString().equalsIgnoreCase(sagaId);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
