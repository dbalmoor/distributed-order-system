package com.deepana.orderservice.service;

import com.deepana.orderservice.dto.request.CreateOrderRequestDTO;
import com.deepana.orderservice.dto.response.OrderResponseDTO;
import com.deepana.orderservice.entity.*;
import com.deepana.orderservice.mapper.OrderMapper;
import com.deepana.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;

    @Override
    public OrderResponseDTO createOrder(CreateOrderRequestDTO request) {

        Order order = orderMapper.toEntity(request);

        Order saved = orderRepository.save(order);

        return orderMapper.toResponse(saved);
    }

    @Override
    public OrderResponseDTO getOrderById(Long id) {

        Order order = orderRepository.findById(id)
                .orElseThrow(() ->
                        new RuntimeException("Order not found: " + id)
                );

        return orderMapper.toResponse(order);
    }

    @Override
    public OrderResponseDTO getByOrderNumber(String orderNumber) {

        Order order = orderRepository
                .findByOrderNumber(orderNumber)
                .orElseThrow(() ->
                        new RuntimeException("Order not found: " + orderNumber)
                );

        return orderMapper.toResponse(order);
    }
}
