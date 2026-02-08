package com.deepana.sagaorchestrator.service;

import com.deepana.sagaorchestrator.dto.*;

public interface SagaService {

    void handleOrderCreated(OrderCreatedEvent event);

    void handleInventoryReserved(InventoryReservedEvent event);

    void handleInventoryFailed(InventoryFailedEvent event);

    void handlePaymentSuccess(PaymentSuccessEvent event);

    void handlePaymentFailed(PaymentFailedEvent event);
}
