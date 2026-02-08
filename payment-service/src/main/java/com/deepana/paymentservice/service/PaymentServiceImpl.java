package com.deepana.paymentservice.service;

import com.deepana.paymentservice.common.logging.SagaLogger;
import com.deepana.paymentservice.entity.Payment;
import com.deepana.paymentservice.events.*;
import com.deepana.paymentservice.kafka.PaymentEventProducer;
import com.deepana.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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

        // ✅ Saga Start Log
        SagaLogger.success(
                "PAYMENT",
                event.getOrderNumber(),
                "PAYMENT_STARTED"
        );

        log.info(
                "Payment started for order {} | Amount={}",
                event.getOrderNumber(),
                event.getAmount()
        );

        // 80% success simulation
        boolean success = random.nextInt(10) < 8;

        // ✅ Create payment entity
        Payment payment = new Payment();

        payment.setOrderId(event.getOrderId());
        payment.setOrderNumber(event.getOrderNumber());

        // ✅ Use BigDecimal from event
        BigDecimal amount = event.getAmount();

        if (amount == null) {
            amount = BigDecimal.ZERO;
            log.warn("Amount is NULL for order {}", event.getOrderNumber());
        }

        payment.setAmount(amount);
        payment.setCreatedAt(LocalDateTime.now());

        if (success) {

            // ✅ SUCCESS FLOW
            payment.setStatus("SUCCESS");
            repository.save(payment);

            PaymentSuccessEvent successEvent = new PaymentSuccessEvent();
            successEvent.setOrderId(event.getOrderId());
            successEvent.setOrderNumber(event.getOrderNumber());
            successEvent.setTraceId(event.getTraceId());

            producer.sendSuccess(successEvent);

            // ✅ Saga Log
            SagaLogger.success(
                    "PAYMENT",
                    event.getOrderNumber(),
                    "PAYMENT_SUCCESS"
            );

            log.info(
                    "Payment SUCCESS for {} | Amount={}",
                    event.getOrderNumber(),
                    amount
            );

        } else {

            // ❌ FAILURE FLOW
            payment.setStatus("FAILED");
            repository.save(payment);

            PaymentFailedEvent failedEvent = new PaymentFailedEvent();
            failedEvent.setOrderId(event.getOrderId());
            failedEvent.setOrderNumber(event.getOrderNumber());
            failedEvent.setReason("Payment gateway declined");
            failedEvent.setTraceId(event.getTraceId());

            producer.sendFailed(failedEvent);

            // ❌ Saga Log
            SagaLogger.failed(
                    "PAYMENT",
                    event.getOrderNumber(),
                    "PAYMENT_FAILED"
            );

            log.warn(
                    "Payment FAILED for {} | Amount={}",
                    event.getOrderNumber(),
                    amount
            );
        }
    }
}
