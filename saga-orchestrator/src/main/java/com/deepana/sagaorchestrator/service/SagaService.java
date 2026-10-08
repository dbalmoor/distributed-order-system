package com.deepana.sagaorchestrator.service;


import com.deepana.saga.commondto.inventory.InventoryFailedEvent;
import com.deepana.saga.commondto.inventory.InventoryReservedEvent;
import com.deepana.saga.commondto.order.OrderCreatedEvent;
import com.deepana.saga.commondto.order.OrderCancelRequestedEvent;
import com.deepana.saga.commondto.order.OrderCancelledEvent;
import com.deepana.saga.commondto.order.OrderConfirmedEvent;
import com.deepana.saga.commondto.payment.PaymentFailedEvent;
import com.deepana.saga.commondto.payment.PaymentSuccessEvent;


public interface SagaService {

    void handleOrderCreated(OrderCreatedEvent event, String messageId);

    void handleInventoryReserved(InventoryReservedEvent event, String messageId);

    void handleInventoryFailed(InventoryFailedEvent event, String messageId);

    void handlePaymentSuccess(PaymentSuccessEvent event, String messageId);

    void handlePaymentFailed(PaymentFailedEvent event, String messageId);

    void handleCancelRequested(OrderCancelRequestedEvent event, String messageId);

    void handleOrderConfirmed(OrderConfirmedEvent event, String messageId);

    void handleOrderCancelled(OrderCancelledEvent event, String messageId);
}
