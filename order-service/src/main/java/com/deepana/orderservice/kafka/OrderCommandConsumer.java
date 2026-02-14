package com.deepana.orderservice.kafka;

import com.deepana.orderservice.service.OrderService;
import com.deepana.saga.commondto.order.CancelOrderCommand;
import com.deepana.saga.commondto.order.ConfirmOrderCommand;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCommandConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    // ================= CONFIRM =================

    @KafkaListener(
            topics = "order.confirm.cmd",
            groupId = "order-group"
    )
    public void onConfirm(String message) throws JsonProcessingException {

        try {
            ConfirmOrderCommand cmd =
                    objectMapper.readValue(message, ConfirmOrderCommand.class);

            log.info("Received order.confirm.cmd: {}", cmd);

            orderService.confirmOrder(cmd);

        } catch (Exception e) {

            log.error("Failed to process order.confirm.cmd", e);
            // Let Kafka retry / DLQ handle
            throw e;
        }
    }

    // ================= CANCEL =================

    @KafkaListener(
            topics = "order.cancel.cmd",
            groupId = "order-group"
    )
    public void onCancel(String message) throws JsonProcessingException {

        try {
            CancelOrderCommand cmd =
                    objectMapper.readValue(message, CancelOrderCommand.class);


            log.info("Received order.cancel.cmd: {}", cmd);

            orderService.cancelBySaga(cmd);

        } catch (Exception e) {

            log.error("Failed to process order.cancel.cmd", e);
            throw e;
        }
    }
}
