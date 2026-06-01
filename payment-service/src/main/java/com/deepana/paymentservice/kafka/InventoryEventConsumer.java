package com.deepana.paymentservice.kafka;

import com.deepana.paymentservice.service.PaymentService;
import com.deepana.saga.commondto.inventory.InventoryReservedEvent;
import com.deepana.saga.commondto.payment.ChargePaymentCommand;
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
}
