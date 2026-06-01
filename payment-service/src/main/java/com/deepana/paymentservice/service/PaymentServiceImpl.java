package com.deepana.paymentservice.service;

import com.deepana.paymentservice.common.logging.SagaLogger;
import com.deepana.paymentservice.entity.Payment;
import com.deepana.paymentservice.kafka.PaymentEventProducer;
import com.deepana.paymentservice.repository.PaymentRepository;
import com.deepana.saga.commondto.base.BaseEvent;
import com.deepana.saga.commondto.inventory.InventoryReservedEvent;
import com.deepana.saga.commondto.payment.ChargePaymentCommand;
import com.deepana.saga.commondto.payment.PaymentFailedEvent;
import com.deepana.saga.commondto.payment.PaymentSuccessEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository repository;
    private final PaymentEventProducer producer;

    private final Random random = new Random();

    @Override
    public void processPayment(ChargePaymentCommand cmd) {

        try {

            MDC.put("traceId", cmd.getTraceId());

            SagaLogger.success(
                    "PAYMENT",
                    cmd.getOrderNumber(),
                    "PAYMENT_STARTED"
            );

            log.info(
                    "Payment started for order {} | Amount={}",
                    cmd.getOrderNumber(),
                    cmd.getTotalAmount()
            );

            // ✅ Persist payment record
            Payment payment = new Payment();

            if (cmd.getTotalAmount()
                    .compareTo(new BigDecimal("10000")) == 0) {

                payment.setStatus("FAILED");
                repository.save(payment);

                PaymentFailedEvent failedEvent = new PaymentFailedEvent();

                copyBaseFields(cmd, failedEvent);

                failedEvent.setTimestamp(Instant.now());
                failedEvent.setReason("Forced failure for testing");
                failedEvent.setTotalAmount(cmd.getTotalAmount());
                failedEvent.setItems(cmd.getItems());

                producer.sendFailed(failedEvent);

                return;
            }

            // 80% success simulation
            boolean success = random.nextInt(10) < 8;


            payment.setOrderId(cmd.getOrderId());
            payment.setOrderNumber(cmd.getOrderNumber());
            payment.setAmount(cmd.getTotalAmount());
            payment.setCreatedAt(LocalDateTime.now());

            if (success) {

                payment.setStatus("SUCCESS");
                repository.save(payment);

                PaymentSuccessEvent successEvent = new PaymentSuccessEvent();

                copyBaseFields(cmd, successEvent);

                successEvent.setTimestamp(Instant.now());
                successEvent.setTotalAmount(cmd.getTotalAmount());
                successEvent.setItems(cmd.getItems());

                producer.sendSuccess(successEvent);

                SagaLogger.success(
                        "PAYMENT",
                        cmd.getOrderNumber(),
                        "PAYMENT_SUCCESS"
                );

                log.info(
                        "Payment SUCCESS for {} | Amount={}",
                        cmd.getOrderNumber(),
                        cmd.getTotalAmount()
                );

            } else {

                payment.setStatus("FAILED");
                repository.save(payment);

                PaymentFailedEvent failedEvent = new PaymentFailedEvent();

                copyBaseFields(cmd, failedEvent);

                failedEvent.setTimestamp(Instant.now());
                failedEvent.setReason("Payment gateway declined");
                failedEvent.setTotalAmount(cmd.getTotalAmount());
                failedEvent.setItems(cmd.getItems());

                producer.sendFailed(failedEvent);

                SagaLogger.failed(
                        "PAYMENT",
                        cmd.getOrderNumber(),
                        "PAYMENT_FAILED"
                );

                log.warn(
                        "Payment FAILED for {} | Amount={}",
                        cmd.getOrderNumber(),
                        cmd.getTotalAmount()
                );
            }

        } finally {
            MDC.clear();
        }
    }

    // ============================================
    // 🔁 Utility: Copy BaseEvent metadata
    // ============================================

    private void copyBaseFields(BaseEvent source, BaseEvent target) {
        target.setSagaId(source.getSagaId());
        target.setOrderId(source.getOrderId());
        target.setOrderNumber(source.getOrderNumber());
        target.setTraceId(source.getTraceId());
    }
}
