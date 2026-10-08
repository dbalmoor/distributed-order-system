package com.deepana.orderservice.service;

import com.deepana.orderservice.dto.request.CreateOrderRequestDTO;
import com.deepana.orderservice.dto.response.OrderResponseDTO;
import com.deepana.saga.commondto.order.CancelOrderCommand;
import com.deepana.saga.commondto.order.ConfirmOrderCommand;

import java.util.List;

public interface OrderService {

    // REST APIs
    OrderResponseDTO createOrder(CreateOrderRequestDTO request);

    OrderResponseDTO getOrderById(Long id);

    OrderResponseDTO getByOrderNumber(String orderNumber);

    List<OrderResponseDTO> getOrdersByUserId(Long userId);

    OrderResponseDTO cancelOrder(Long orderId);


    void confirmOrder(ConfirmOrderCommand cmd);

    void cancelBySaga(CancelOrderCommand cmd);
}
