package com.deepana.sagaorchestrator.kafka;

import com.deepana.sagaorchestrator.commands.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class SagaCommandProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;


    // ================= INVENTORY =================

    public void sendReserveInventory(ReserveInventoryCommand cmd) {
        send("inventory.reserve.cmd", cmd.getOrderNumber(), cmd);
    }

    public void sendReleaseInventory(ReleaseInventoryCommand cmd) {
        send("inventory.release.cmd", cmd.getOrderNumber(), cmd);
    }


    // ================= PAYMENT =================

    public void sendChargePayment(ChargePaymentCommand cmd) {
        send("payment.charge.cmd", cmd.getOrderNumber(), cmd);
    }

    public void sendRefundPayment(RefundPaymentCommand cmd) {
        send("payment.refund.cmd", cmd.getOrderNumber(), cmd);
    }


    // ================= ORDER =================

    public void sendConfirmOrder(ConfirmOrderCommand cmd) {
        send("order.confirm.cmd", cmd.getOrderNumber(), cmd);
    }

    public void sendCancelOrder(CancelOrderCommand cmd) {
        send("order.cancel.cmd", cmd.getOrderNumber(), cmd);
    }


    // ================= GENERIC =================

    private void send(String topic, String key, Object payload) {

        try {

            String json = objectMapper.writeValueAsString(payload);

            kafkaTemplate.send(topic, key, json);

            log.info("Saga CMD [{}] => {}", topic, json);

        } catch (Exception e) {

            log.error("Failed to send saga command {}", topic, e);

            throw new RuntimeException(e);
        }
    }
}
