package com.deepana.paymentservice;

import com.deepana.paymentservice.entity.Payment;
import com.deepana.paymentservice.entity.PaymentType;
import com.deepana.paymentservice.kafka.PaymentEventProducer;
import com.deepana.paymentservice.repository.PaymentRepository;
import com.deepana.paymentservice.service.PaymentService;
import com.deepana.saga.commondto.payment.ChargePaymentCommand;
import com.deepana.saga.commondto.payment.PaymentFailedEvent;
import com.deepana.saga.commondto.payment.PaymentSuccessEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = "payment.fail-amount-threshold=100.00")
class PaymentIdempotencyIntegrationTest extends IntegrationTestBase {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockBean
    private PaymentEventProducer producer;

    @BeforeEach
    void cleanDatabaseAndMocks() {
        paymentRepository.deleteAllInBatch();
        reset(producer);
    }

    @Test
    void duplicateCharge_reusesStoredSuccessfulPaymentAndOutcome() {
        ChargePaymentCommand command = command("45.00");

        paymentService.processPayment(command);
        paymentService.processPayment(command);

        List<Payment> payments = paymentRepository.findAll();
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getSagaId()).isEqualTo(command.getSagaId());
        assertThat(payments.get(0).getType()).isEqualTo(PaymentType.CHARGE);
        assertThat(payments.get(0).getStatus()).isEqualTo("SUCCESS");
        verify(producer, times(2)).sendSuccess(any(PaymentSuccessEvent.class));
    }

    @Test
    void configuredThresholdFailsMatchingAmountsAndOtherAmountsSucceed() {
        ChargePaymentCommand failingCommand = command("100.00");
        ChargePaymentCommand successfulCommand = command("99.99");

        paymentService.processPayment(failingCommand);
        paymentService.processPayment(failingCommand);
        paymentService.processPayment(successfulCommand);

        assertThat(paymentRepository.count()).isEqualTo(2);
        assertThat(paymentRepository.findBySagaIdAndType(failingCommand.getSagaId(), PaymentType.CHARGE))
                .hasValueSatisfying(payment -> assertThat(payment.getStatus()).isEqualTo("FAILED"));
        assertThat(paymentRepository.findBySagaIdAndType(successfulCommand.getSagaId(), PaymentType.CHARGE))
                .hasValueSatisfying(payment -> assertThat(payment.getStatus()).isEqualTo("SUCCESS"));
        verify(producer, times(2)).sendFailed(any(PaymentFailedEvent.class));
        verify(producer).sendSuccess(any(PaymentSuccessEvent.class));
    }

    @Test
    void concurrentDuplicateCharges_createOnePaymentAndEmitSameOutcomeTwice() throws Exception {
        ChargePaymentCommand command = command("45.00");
        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> processAfter(start, command));
            Future<?> second = executor.submit(() -> processAfter(start, command));

            start.countDown();
            first.get(30, TimeUnit.SECONDS);
            second.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(paymentRepository.count()).isEqualTo(1);
        assertThat(paymentRepository.findBySagaIdAndType(command.getSagaId(), PaymentType.CHARGE))
                .hasValueSatisfying(payment -> assertThat(payment.getStatus()).isEqualTo("SUCCESS"));
        verify(producer, times(2)).sendSuccess(any(PaymentSuccessEvent.class));
    }

    private void processAfter(CountDownLatch start, ChargePaymentCommand command) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start concurrent charge");
            }
            paymentService.processPayment(command);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent charge test was interrupted", e);
        }
    }

    private ChargePaymentCommand command(String amount) {
        ChargePaymentCommand command = new ChargePaymentCommand();
        command.setSagaId(UUID.randomUUID().toString());
        command.setOrderId(27L);
        command.setOrderNumber("ORD-27");
        command.setTraceId(UUID.randomUUID().toString());
        command.setTotalAmount(new BigDecimal(amount));
        command.setItems(List.of());
        return command;
    }
}
