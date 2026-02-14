package com.deepana.paymentservice.service;

import com.deepana.paymentservice.common.logging.SagaLogger;
import com.deepana.paymentservice.entity.Payment;
import com.deepana.paymentservice.kafka.PaymentEventProducer;
import com.deepana.paymentservice.repository.PaymentRepository;
import com.deepana.saga.commondto.base.BaseEvent;
import com.deepana.saga.commondto.inventory.InventoryReservedEvent;
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
    public void processPayment(InventoryReservedEvent event) {

        try {

            MDC.put("traceId", event.getTraceId());

            SagaLogger.success(
                    "PAYMENT",
                    event.getOrderNumber(),
                    "PAYMENT_STARTED"
            );

            log.info(
                    "Payment started for order {} | Amount={}",
                    event.getOrderNumber(),
                    event.getTotalAmount()
            );

            // ✅ Persist payment record
            Payment payment = new Payment();

            if (event.getTotalAmount()
                    .compareTo(new BigDecimal("10000")) == 0) {

                payment.setStatus("FAILED");
                repository.save(payment);

                PaymentFailedEvent failedEvent = new PaymentFailedEvent();

                failedEvent.setSagaId(event.getSagaId());
                failedEvent.setOrderId(event.getOrderId());
                failedEvent.setOrderNumber(event.getOrderNumber());
                failedEvent.setTraceId(event.getTraceId());
                failedEvent.setTimestamp(Instant.now());
                failedEvent.setReason("Forced failure for testing");


                producer.sendFailed(failedEvent);

                return;
            }

            // 80% success simulation
            boolean success = random.nextInt(10) < 8;


            payment.setOrderId(event.getOrderId());
            payment.setOrderNumber(event.getOrderNumber());
            payment.setAmount(event.getTotalAmount());
            payment.setCreatedAt(LocalDateTime.now());

            if (success) {

                payment.setStatus("SUCCESS");
                repository.save(payment);

                PaymentSuccessEvent successEvent = new PaymentSuccessEvent();
                copyBaseFields(event, successEvent);

                successEvent.setTimestamp(Instant.now());

                producer.sendSuccess(successEvent);

                SagaLogger.success(
                        "PAYMENT",
                        event.getOrderNumber(),
                        "PAYMENT_SUCCESS"
                );

                log.info(
                        "Payment SUCCESS for {} | Amount={}",
                        event.getOrderNumber(),
                        event.getTotalAmount()
                );

            } else {

                payment.setStatus("FAILED");
                repository.save(payment);

                PaymentFailedEvent failedEvent = new PaymentFailedEvent();
                copyBaseFields(event, failedEvent);

                failedEvent.setReason("Payment gateway declined");
                failedEvent.setTimestamp(Instant.now());

                producer.sendFailed(failedEvent);

                SagaLogger.failed(
                        "PAYMENT",
                        event.getOrderNumber(),
                        "PAYMENT_FAILED"
                );

                log.warn(
                        "Payment FAILED for {} | Amount={}",
                        event.getOrderNumber(),
                        event.getTotalAmount()
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
