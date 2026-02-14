package com.deepana.orderservice.service;

import com.deepana.orderservice.dto.request.CreateOrderRequestDTO;
import com.deepana.orderservice.dto.response.OrderResponseDTO;
import com.deepana.saga.commondto.inventory.InventoryFailedEvent;
import com.deepana.saga.commondto.inventory.InventoryReservedEvent;
import com.deepana.saga.commondto.order.CancelOrderCommand;
import com.deepana.saga.commondto.order.ConfirmOrderCommand;
import com.deepana.saga.commondto.payment.PaymentFailedEvent;
import com.deepana.saga.commondto.payment.PaymentSuccessEvent;

import java.util.List;

public interface OrderService {

    // REST APIs
    OrderResponseDTO createOrder(CreateOrderRequestDTO request);

    OrderResponseDTO getOrderById(Long id);

    OrderResponseDTO getByOrderNumber(String orderNumber);

    List<OrderResponseDTO> getOrdersByUserId(Long userId);

    OrderResponseDTO cancelOrder(Long orderId);


    // Saga callbacks
    void handleInventoryReserved(InventoryReservedEvent event);

    void handleInventoryFailed(InventoryFailedEvent event);

    void handlePaymentSuccess(PaymentSuccessEvent event);

    void handlePaymentFailure(PaymentFailedEvent event);

    void confirmOrder(ConfirmOrderCommand cmd);

    void cancelBySaga(CancelOrderCommand cmd);
}

