package com.deepana.orderservice.service;

import com.deepana.orderservice.dto.request.CreateOrderRequestDTO;
import com.deepana.orderservice.dto.response.OrderResponseDTO;

public interface OrderService {

    OrderResponseDTO createOrder(CreateOrderRequestDTO request);

    OrderResponseDTO getOrderById(Long id);

    OrderResponseDTO getByOrderNumber(String orderNumber);
}

