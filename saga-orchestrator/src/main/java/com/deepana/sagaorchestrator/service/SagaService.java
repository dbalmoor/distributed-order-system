package com.deepana.sagaorchestrator.service;


import com.deepana.saga.commondto.inventory.InventoryFailedEvent;
import com.deepana.saga.commondto.inventory.InventoryReservedEvent;
import com.deepana.saga.commondto.order.OrderCreatedEvent;
import com.deepana.saga.commondto.payment.PaymentFailedEvent;
import com.deepana.saga.commondto.payment.PaymentSuccessEvent;


public interface SagaService {

    void handleOrderCreated(OrderCreatedEvent event);

    void handleInventoryReserved(InventoryReservedEvent event);

    void handleInventoryFailed(InventoryFailedEvent event);

    void handlePaymentSuccess(PaymentSuccessEvent event);

    void handlePaymentFailed(PaymentFailedEvent event);
}
