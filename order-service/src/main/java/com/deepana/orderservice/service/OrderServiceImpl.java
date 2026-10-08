package com.deepana.orderservice.service;

import com.deepana.orderservice.dto.request.CreateOrderRequestDTO;
import com.deepana.orderservice.dto.response.OrderResponseDTO;
import com.deepana.orderservice.entity.Order;
import com.deepana.orderservice.entity.OrderStatus;
import com.deepana.orderservice.exception.ResourceNotFoundException;
import com.deepana.orderservice.kafka.OrderEventProducer;
import com.deepana.orderservice.mapper.OrderMapper;
import com.deepana.orderservice.repository.OrderRepository;
import com.deepana.saga.commondto.base.BaseEvent;
import com.deepana.saga.commondto.order.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final OrderEventProducer orderEventProducer;
    private final OrderStateMachine stateMachine;

    // ================= CREATE =================

    @Override
    @Transactional
    public OrderResponseDTO createOrder(CreateOrderRequestDTO request) {

        Order order = orderMapper.toEntity(request);

        order.setStatus(OrderStatus.CREATED);

        Order saved = orderRepository.save(order);

        String sagaId = UUID.randomUUID().toString();
        String traceId = saved.getOrderNumber();

        OrderCreatedEvent event =
                orderMapper.toSagaEvent(saved, sagaId, traceId);

        orderEventProducer.sendOrderCreated(event);

        log.info("Order created and saga started {}", saved.getId());

        return orderMapper.toResponse(saved);
    }


    @Override
    public OrderResponseDTO getOrderById(Long id) {

        Order order = orderRepository.findById(id)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Order not found: " + id)
                );

        return orderMapper.toResponse(order);
    }


    @Override
    public OrderResponseDTO getByOrderNumber(String orderNumber) {

        Order order = orderRepository
                .findByOrderNumber(orderNumber)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Order not found: " + orderNumber)
                );

        return orderMapper.toResponse(order);
    }


    @Override
    public List<OrderResponseDTO> getOrdersByUserId(Long userId) {

        List<Order> orders =
                orderRepository.findByUserId(userId);

        if (orders.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No orders found for user: " + userId);
        }

        return orders.stream()
                .map(orderMapper::toResponse)
                .toList();
    }


    @Override
    @Transactional
    public OrderResponseDTO cancelOrder(Long orderId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Order not found: " + orderId)
                );

        OrderCancelRequestedEvent event = new OrderCancelRequestedEvent();
        event.setOrderId(order.getId());
        event.setOrderNumber(order.getOrderNumber());
        event.setTraceId(order.getOrderNumber());
        event.setTimestamp(java.time.Instant.now());
        afterCommit(() -> orderEventProducer.sendCancelRequested(event));
        log.info("Cancellation requested for order {}", orderId);

        return orderMapper.toResponse(order);
    }

    // ================= CONFIRM =================

    @Override
    @Transactional
    public void confirmOrder(ConfirmOrderCommand cmd) {

        Order order = orderRepository.findByIdForUpdate(cmd.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + cmd.getOrderId()));

        if (stateMachine.transition(order.getStatus(), OrderStatus.COMPLETED, order.getId())) {
            order.setStatus(OrderStatus.COMPLETED);
            orderRepository.save(order);

            OrderConfirmedEvent event = new OrderConfirmedEvent();
            copyCommandFields(cmd, order, event);
            afterCommit(() -> orderEventProducer.sendOrderConfirmed(event));
        }
    }

    // ================= CANCEL =================

    @Override
    @Transactional
    public void cancelBySaga(CancelOrderCommand cmd) {

        Order order = orderRepository.findByIdForUpdate(cmd.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + cmd.getOrderId()));

        if (stateMachine.transition(order.getStatus(), OrderStatus.CANCELLED, order.getId())) {
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.save(order);

            OrderCancelledEvent event = new OrderCancelledEvent();
            copyCommandFields(cmd, order, event);
            afterCommit(() -> orderEventProducer.sendOrderCancelled(event));
        }
    }

    private void copyCommandFields(
            BaseEvent command,
            Order order,
            BaseEvent event) {
        event.setOrderId(order.getId());
        event.setOrderNumber(order.getOrderNumber());
        event.setSagaId(command.getSagaId());
        event.setTraceId(command.getTraceId());
        event.setTimestamp(java.time.Instant.now());
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Order event publication requires an active transaction");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
