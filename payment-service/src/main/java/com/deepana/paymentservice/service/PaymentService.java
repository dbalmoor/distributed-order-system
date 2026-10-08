package com.deepana.paymentservice.service;


import com.deepana.saga.commondto.inventory.InventoryReservedEvent;
import com.deepana.saga.commondto.payment.ChargePaymentCommand;
import com.deepana.saga.commondto.payment.RefundPaymentCommand;

public interface PaymentService {

    void processPayment(ChargePaymentCommand cmd);

    void processRefund(RefundPaymentCommand cmd);

}
