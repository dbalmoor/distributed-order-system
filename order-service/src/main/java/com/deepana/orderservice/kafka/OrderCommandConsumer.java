package com.deepana.orderservice.kafka;

import com.deepana.orderservice.commands.CancelOrderCommand;
import com.deepana.orderservice.commands.ConfirmOrderCommand;
import com.deepana.orderservice.service.OrderService;
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
    private final ObjectMapper mapper;

    // CONFIRM

    @KafkaListener(topics = "order.confirm.cmd", groupId = "order-group")
    public void onConfirm(String msg) {

        try {

            ConfirmOrderCommand cmd =
                    mapper.readValue(msg, ConfirmOrderCommand.class);

            orderService.confirmOrder(cmd);

        } catch (Exception e) {

            log.error("order.confirm.cmd failed", e);
            throw new RuntimeException(e);
        }
    }

    // CANCEL

    @KafkaListener(topics = "order.cancel.cmd", groupId = "order-group")
    public void onCancel(String msg) {

        try {

            CancelOrderCommand cmd =
                    mapper.readValue(msg, CancelOrderCommand.class);

            orderService.cancelBySaga(cmd);

        } catch (Exception e) {

            log.error("order.cancel.cmd failed", e);
            throw new RuntimeException(e);
        }
    }
}

