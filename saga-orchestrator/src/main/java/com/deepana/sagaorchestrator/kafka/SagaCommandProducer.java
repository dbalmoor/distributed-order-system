package com.deepana.sagaorchestrator.kafka;

import com.deepana.saga.commondto.inventory.ReleaseInventoryCommand;
import com.deepana.saga.commondto.inventory.ReserveInventoryCommand;
import com.deepana.saga.commondto.order.CancelOrderCommand;
import com.deepana.saga.commondto.order.ConfirmOrderCommand;
import com.deepana.saga.commondto.payment.ChargePaymentCommand;
import com.deepana.saga.commondto.payment.RefundPaymentCommand;
import com.deepana.saga.commondto.base.BaseEvent;
import com.deepana.sagaorchestrator.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SagaCommandProducer {

    private final OutboxWriter outboxWriter;


    // ================= INVENTORY =================

    public void sendReserveInventory(ReserveInventoryCommand cmd) {
        send("inventory.reserve.cmd", cmd);
    }

    public void sendReleaseInventory(ReleaseInventoryCommand cmd) {
        send("inventory.release.cmd", cmd);
    }


    // ================= PAYMENT =================

    public void sendChargePayment(ChargePaymentCommand cmd) {
        send("payment.charge.cmd", cmd);
    }

    public void sendRefundPayment(RefundPaymentCommand cmd) {
        send("payment.refund.cmd", cmd);
    }


    // ================= ORDER =================

    public void sendConfirmOrder(ConfirmOrderCommand cmd) {
        send("order.confirm.cmd", cmd);
    }

    public void sendCancelOrder(CancelOrderCommand cmd) {
        send("order.cancel.cmd", cmd);
    }


    // ================= GENERIC =================

    private void send(String topic, BaseEvent payload) {
        if (payload.getOrderId() == null || payload.getSagaId() == null) {
            throw new IllegalArgumentException("Saga commands require sagaId and orderId");
        }
        outboxWriter.write(topic, payload);
        log.info("Saga CMD [{}] queued in outbox for order {}", topic, payload.getOrderId());
    }
}
