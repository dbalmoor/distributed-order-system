package com.deepana.paymentservice.service;


import com.deepana.saga.commondto.inventory.InventoryReservedEvent;

public interface PaymentService {

    void processPayment(InventoryReservedEvent event);

}
