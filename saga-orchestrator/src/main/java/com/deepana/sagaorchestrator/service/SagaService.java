package com.deepana.sagaorchestrator.service;


import com.deepana.saga.commondto.inventory.InventoryFailedEvent;
import com.deepana.saga.commondto.inventory.InventoryReservedEvent;
import com.deepana.saga.commondto.inventory.InventoryReleasedEvent;
import com.deepana.saga.commondto.order.OrderCreatedEvent;
import com.deepana.saga.commondto.order.OrderCancelRequestedEvent;
import com.deepana.saga.commondto.order.OrderCancelledEvent;
import com.deepana.saga.commondto.order.OrderConfirmedEvent;
import com.deepana.saga.commondto.payment.PaymentFailedEvent;
import com.deepana.saga.commondto.payment.PaymentSuccessEvent;
import com.deepana.saga.commondto.payment.PaymentRefundedEvent;
import com.deepana.sagaorchestrator.repository.SagaAdminView;

import java.util.List;


public interface SagaService {

    void handleOrderCreated(OrderCreatedEvent event, String messageId);

    void handleInventoryReserved(InventoryReservedEvent event, String messageId);

    void handleInventoryFailed(InventoryFailedEvent event, String messageId);

    void handleInventoryReleased(InventoryReleasedEvent event, String messageId);

    void handlePaymentSuccess(PaymentSuccessEvent event, String messageId);

    void handlePaymentFailed(PaymentFailedEvent event, String messageId);

    void handlePaymentRefunded(PaymentRefundedEvent event, String messageId);

    void handleCancelRequested(OrderCancelRequestedEvent event, String messageId);

    void handleOrderConfirmed(OrderConfirmedEvent event, String messageId);

    void handleOrderCancelled(OrderCancelledEvent event, String messageId);

    List<SagaAdminView> listNeedingAttention();

    void retryNeedsAttention(String sagaId, String action);

    void forceResolve(String sagaId, String operatorNote);
}
