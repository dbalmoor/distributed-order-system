package com.deepana.orderservice.service;

import com.deepana.orderservice.commands.CancelOrderCommand;
import com.deepana.orderservice.commands.ConfirmOrderCommand;
import com.deepana.orderservice.dto.request.CreateOrderRequestDTO;
import com.deepana.orderservice.dto.response.OrderResponseDTO;
import com.deepana.orderservice.events.InventoryFailedEvent;
import com.deepana.orderservice.events.InventoryReservedEvent;
import com.deepana.orderservice.events.PaymentFailedEvent;
import com.deepana.orderservice.events.PaymentSuccessEvent;

import java.util.List;

public interface OrderService {

    OrderResponseDTO createOrder(CreateOrderRequestDTO request);

    OrderResponseDTO getOrderById(Long id);

    OrderResponseDTO getByOrderNumber(String orderNumber);

    List<OrderResponseDTO> getOrdersByUserId(Long userId);

    OrderResponseDTO cancelOrder(Long orderId);

    void handleInventoryReserved(InventoryReservedEvent event);

    void handleInventoryFailed(InventoryFailedEvent event);

    void handlePaymentSuccess(PaymentSuccessEvent event);

    void handlePaymentFailure(PaymentFailedEvent event);


    void confirmOrder(ConfirmOrderCommand cmd);

    void cancelBySaga(CancelOrderCommand cmd);
}

