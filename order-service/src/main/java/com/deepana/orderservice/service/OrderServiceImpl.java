package com.deepana.orderservice.service;

import com.deepana.orderservice.common.logging.SagaLogger;
import com.deepana.orderservice.dto.request.CreateOrderRequestDTO;
import com.deepana.orderservice.dto.response.OrderResponseDTO;
import com.deepana.orderservice.entity.*;
import com.deepana.orderservice.events.*;
import com.deepana.orderservice.exception.ResourceNotFoundException;
import com.deepana.orderservice.kafka.OrderEventProducer;
import com.deepana.orderservice.mapper.OrderMapper;
import com.deepana.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final OrderEventProducer orderEventProducer;

    // ================= CREATE =================

    @Override
    @Transactional
    public OrderResponseDTO createOrder(CreateOrderRequestDTO request) {

        try {

            Order order = orderMapper.toEntity(request);

            Order saved = orderRepository.save(order);

            MDC.put("traceId", saved.getOrderNumber());

            OrderCreatedEvent event =
                    orderMapper.mapToEvent(saved);

            orderEventProducer.sendOrderCreatedEvent(event);

            SagaLogger.success(
                    "ORDER",
                    saved.getOrderNumber(),
                    "ORDER_CREATED"
            );

            return orderMapper.toResponse(saved);

        } finally {
            MDC.clear();
        }
    }

    // ================= READ =================

    @Override
    public OrderResponseDTO getOrderById(Long id) {

        Order order = orderRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Order not found"));

        return orderMapper.toResponse(order);
    }

    @Override
    public OrderResponseDTO getByOrderNumber(String orderNumber) {

        Order order = orderRepository
                .findByOrderNumber(orderNumber)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Order not found"));

        return orderMapper.toResponse(order);
    }

    @Override
    public List<OrderResponseDTO> getOrdersByUserId(Long userId) {

        List<Order> orders =
                orderRepository.findByUserId(userId);

        if (orders.isEmpty()) {
            throw new ResourceNotFoundException("No orders");
        }

        return orders.stream()
                .map(orderMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public OrderResponseDTO cancelOrder(Long orderId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow();

        if (order.getStatus() == OrderStatus.COMPLETED ||
                order.getStatus() == OrderStatus.FAILED ||
                order.getStatus() == OrderStatus.CANCELLED) {

            throw new IllegalStateException(
                    "Cannot cancel order in " + order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);

        orderRepository.save(order);

        return orderMapper.toResponse(order);
    }

    // ================= INVENTORY SUCCESS =================

    @Override
    @Transactional
    public void handleInventoryReserved(InventoryReservedEvent event) {

        try {

            MDC.put("traceId", event.getTraceId());

            Order order =
                    orderRepository.findByIdForUpdate(
                                    event.getOrderId())
                            .orElseThrow();

            if (order.getStatus() == OrderStatus.COMPLETED ||
                    order.getStatus() == OrderStatus.FAILED) {
                return;
            }

            if (order.getStatus() ==
                    OrderStatus.PAYMENT_FAILED_PENDING) {

                order.setStatus(OrderStatus.FAILED);

                orderRepository.save(order);

                SagaLogger.failed(
                        "ORDER",
                        event.getOrderNumber(),
                        "PAYMENT_FAILED_AFTER_INVENTORY");

                return;
            }

            if (order.getStatus() ==
                    OrderStatus.CREATED ||
                    order.getStatus() ==
                            OrderStatus.PAYMENT_SUCCESS_PENDING) {

                order.setStatus(
                        OrderStatus.INVENTORY_RESERVED);

                orderRepository.save(order);

                SagaLogger.success(
                        "ORDER",
                        event.getOrderNumber(),
                        "INVENTORY_RESERVED");

                log.info("Inventory reserved {}",
                        event.getOrderNumber());
            }

        } finally {
            MDC.clear();
        }
    }

    // ================= INVENTORY FAILED =================

    @Override
    @Transactional
    public void handleInventoryFailed(InventoryFailedEvent event) {

        try {

            MDC.put("traceId", event.getTraceId());

            Order order =
                    orderRepository.findByIdForUpdate(
                                    event.getOrderId())
                            .orElseThrow();

            if (order.getStatus() ==
                    OrderStatus.COMPLETED) {
                return;
            }

            order.setStatus(OrderStatus.FAILED);

            orderRepository.save(order);

            SagaLogger.failed(
                    "ORDER",
                    event.getOrderNumber(),
                    "INVENTORY_FAILED");

        } finally {
            MDC.clear();
        }
    }

    // ================= PAYMENT SUCCESS =================

    @Override
    @Transactional
    public void handlePaymentSuccess(PaymentSuccessEvent event) {

        try {

            MDC.put("traceId", event.getTraceId());

            Order order =
                    orderRepository.findByIdForUpdate(
                                    event.getOrderId())
                            .orElseThrow();

            if (order.getStatus() ==
                    OrderStatus.COMPLETED ||
                    order.getStatus() ==
                            OrderStatus.FAILED) {
                return;
            }

            if (order.getStatus() ==
                    OrderStatus.INVENTORY_RESERVED) {

                order.setStatus(OrderStatus.COMPLETED);

                orderRepository.save(order);

                SagaLogger.success(
                        "ORDER",
                        event.getOrderNumber(),
                        "PAYMENT_SUCCESS");

                log.info("Order {} COMPLETED",
                        event.getOrderNumber());

                return;
            }

            if (order.getStatus() ==
                    OrderStatus.CREATED) {

                order.setStatus(
                        OrderStatus.PAYMENT_SUCCESS_PENDING);

                orderRepository.save(order);

                log.info("Payment pending {}",
                        event.getOrderNumber());
            }

        } finally {
            MDC.clear();
        }
    }

    // ================= PAYMENT FAILED =================

    @Override
    @Transactional
    public void handlePaymentFailure(PaymentFailedEvent event) {

        try {

            MDC.put("traceId", event.getTraceId());

            Order order =
                    orderRepository.findByIdForUpdate(
                                    event.getOrderId())
                            .orElseThrow();

            if (order.getStatus() ==
                    OrderStatus.COMPLETED ||
                    order.getStatus() ==
                            OrderStatus.FAILED) {
                return;
            }

            if (order.getStatus() ==
                    OrderStatus.INVENTORY_RESERVED) {

                order.setStatus(OrderStatus.FAILED);

                orderRepository.save(order);

                SagaLogger.failed(
                        "ORDER",
                        event.getOrderNumber(),
                        "PAYMENT_FAILED");

                return;
            }

            if (order.getStatus() ==
                    OrderStatus.CREATED) {

                order.setStatus(
                        OrderStatus.PAYMENT_FAILED_PENDING);

                orderRepository.save(order);

                log.info("Payment failed pending {}",
                        event.getOrderNumber());
            }

        } finally {
            MDC.clear();
        }
    }
}
