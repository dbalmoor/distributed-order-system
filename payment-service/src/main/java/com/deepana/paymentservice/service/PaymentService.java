package com.deepana.paymentservice.service;


import com.deepana.saga.commondto.inventory.InventoryReservedEvent;
import com.deepana.saga.commondto.payment.ChargePaymentCommand;

public interface PaymentService {

    void processPayment(ChargePaymentCommand cmd);

}
