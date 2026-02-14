package com.deepana.orderservice.service;

import com.deepana.orderservice.dto.request.CreateOrderRequestDTO;
import com.deepana.orderservice.dto.response.OrderResponseDTO;
import com.deepana.orderservice.entity.Order;
import com.deepana.orderservice.entity.OrderStatus;
import com.deepana.orderservice.exception.ResourceNotFoundException;
import com.deepana.orderservice.kafka.OrderEventProducer;
import com.deepana.orderservice.mapper.OrderMapper;
import com.deepana.orderservice.repository.OrderRepository;
import com.deepana.saga.commondto.inventory.InventoryFailedEvent;
import com.deepana.saga.commondto.inventory.InventoryReservedEvent;
import com.deepana.saga.commondto.order.*;
import com.deepana.saga.commondto.payment.PaymentFailedEvent;
import com.deepana.saga.commondto.payment.PaymentSuccessEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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

        // Prevent cancelling final states
        if (order.getStatus() == OrderStatus.COMPLETED ||
                order.getStatus() == OrderStatus.FAILED ||
                order.getStatus() == OrderStatus.CANCELLED) {

            throw new IllegalStateException(
                    "Cannot cancel order in state: " + order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);

        orderRepository.save(order);

        log.warn("Order {} manually cancelled", orderId);

        return orderMapper.toResponse(order);
    }


    // ================= INVENTORY SUCCESS =================

    @Override
    @Transactional
    public void handleInventoryReserved(InventoryReservedEvent event) {

        MDC.put("traceId", event.getTraceId());

        Order order = orderRepository
                .findByIdForUpdate(event.getOrderId())
                .orElseThrow();

        if (order.getStatus() != OrderStatus.CREATED) {
            return;
        }

        order.setStatus(OrderStatus.INVENTORY_RESERVED);

        orderRepository.save(order);

        log.info("Inventory reserved for order {}", order.getId());

        MDC.clear();
    }

    // ================= INVENTORY FAILED =================

    @Override
    @Transactional
    public void handleInventoryFailed(InventoryFailedEvent event) {

        MDC.put("traceId", event.getTraceId());

        Order order = orderRepository
                .findByIdForUpdate(event.getOrderId())
                .orElseThrow();

        if (order.getStatus() == OrderStatus.FAILED) {
            return;
        }

        order.setStatus(OrderStatus.FAILED);

        orderRepository.save(order);

        log.warn("Inventory failed for order {}", order.getId());

        MDC.clear();
    }

    // ================= PAYMENT SUCCESS =================

    @Override
    @Transactional
    public void handlePaymentSuccess(PaymentSuccessEvent event) {

        MDC.put("traceId", event.getTraceId());

        Order order = orderRepository
                .findByIdForUpdate(event.getOrderId())
                .orElseThrow();

        if (order.getStatus() != OrderStatus.INVENTORY_RESERVED) {
            return;
        }

        order.setStatus(OrderStatus.COMPLETED);

        orderRepository.save(order);

        log.info("Payment success, order completed {}", order.getId());

        MDC.clear();
    }

    // ================= PAYMENT FAILED =================

    @Override
    @Transactional
    public void handlePaymentFailure(PaymentFailedEvent event) {

        MDC.put("traceId", event.getTraceId());

        Order order = orderRepository
                .findByIdForUpdate(event.getOrderId())
                .orElseThrow();

        if (order.getStatus() == OrderStatus.FAILED) {
            return;
        }

        order.setStatus(OrderStatus.FAILED);

        orderRepository.save(order);

        log.warn("Payment failed for order {}", order.getId());

        MDC.clear();
    }

    // ================= CONFIRM =================

    @Override
    @Transactional
    public void confirmOrder(ConfirmOrderCommand cmd) {

        Order order = orderRepository
                .findById(cmd.getOrderId())
                .orElseThrow();

        if (order.getStatus() == OrderStatus.COMPLETED) {
            return;
        }

        order.setStatus(OrderStatus.COMPLETED);

        orderRepository.save(order);

        log.info("Order confirmed {}", order.getId());
    }

    // ================= CANCEL =================

    @Override
    @Transactional
    public void cancelBySaga(CancelOrderCommand cmd) {

        Order order = orderRepository
                .findById(cmd.getOrderId())
                .orElseThrow();

        if (order.getStatus() == OrderStatus.CANCELLED ||
                order.getStatus() == OrderStatus.FAILED) {
            return;
        }

        order.setStatus(OrderStatus.CANCELLED);

        orderRepository.save(order);

        log.warn("Order cancelled by saga {}", order.getId());
    }
}
