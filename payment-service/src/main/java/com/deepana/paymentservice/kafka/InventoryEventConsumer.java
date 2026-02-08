package com.deepana.paymentservice.kafka;

import com.deepana.paymentservice.events.InventoryReservedEvent;
import com.deepana.paymentservice.service.PaymentService;
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
            topics = "inventory.reserved",
            groupId = "payment-group"
    )
    public void consume(String message) throws JsonProcessingException {

        try {



            InventoryReservedEvent event =
                    objectMapper.readValue(message, InventoryReservedEvent.class);

            MDC.put("traceId", event.getTraceId());

            log.info("Received inventory.reserved {}", message);

            paymentService.processPayment(event);


        } catch (Exception e) {
            log.error(
                    "Failed to consume inventory event. Message={}",
                    message,
                    e
            );
            throw e;
        } finally {

            MDC.clear(); // important
        }

    }
}
