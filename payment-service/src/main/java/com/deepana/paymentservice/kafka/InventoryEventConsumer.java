package com.deepana.paymentservice.kafka;

import com.deepana.paymentservice.service.PaymentService;
import com.deepana.saga.commondto.payment.ChargePaymentCommand;
import com.deepana.saga.commondto.payment.RefundPaymentCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventConsumer {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "payment.charge.cmd",
            groupId = "payment-group"
    )
    public void consume(String message) throws JsonProcessingException {

        try {

            ChargePaymentCommand cmd =
                    objectMapper.readValue(message, ChargePaymentCommand.class);

            MDC.put("traceId", cmd.getTraceId());

            log.info("Received payment.charge.cmd {}", message);

            paymentService.processPayment(cmd);

        } finally {
            MDC.clear();
        }
    }

    @KafkaListener(
            topics = "payment.refund.cmd",
            groupId = "payment-group"
    )
    public void consumeRefund(String message) throws JsonProcessingException {
        try {
            RefundPaymentCommand cmd = objectMapper.readValue(message, RefundPaymentCommand.class);
            MDC.put("traceId", cmd.getTraceId());
            log.info("Received payment.refund.cmd for order {}", cmd.getOrderId());
            paymentService.processRefund(cmd);
        } finally {
            MDC.clear();
        }
    }
}
