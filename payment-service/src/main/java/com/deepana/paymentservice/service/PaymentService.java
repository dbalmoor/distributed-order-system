package com.deepana.paymentservice.service;

import com.deepana.paymentservice.events.InventoryReservedEvent;

public interface PaymentService {

    void processPayment(InventoryReservedEvent event);

}
