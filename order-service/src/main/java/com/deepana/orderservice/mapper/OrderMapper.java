package com.deepana.orderservice.mapper;

import com.deepana.orderservice.dto.request.CreateOrderRequestDTO;
import com.deepana.orderservice.dto.response.OrderItemResponseDTO;
import com.deepana.orderservice.dto.response.OrderResponseDTO;
import com.deepana.orderservice.entity.Order;
import com.deepana.orderservice.entity.OrderItem;
import com.deepana.orderservice.entity.OrderStatus;

import com.deepana.saga.commondto.order.OrderCreatedEvent;
import com.deepana.saga.commondto.order.OrderItemEvent;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class OrderMapper {

    // ================= ENTITY =================

    public Order toEntity(CreateOrderRequestDTO dto) {

        Order order = new Order();

        order.setOrderNumber(generateOrderNumber());
        order.setUserId(dto.getUserId());
        order.setStatus(OrderStatus.CREATED);
        order.setPaymentType(dto.getPaymentType());
        order.setFulfillmentType(dto.getFulfillmentType());

        List<OrderItem> items = dto.getItems()
                .stream()
                .map(itemDto -> {

                    OrderItem item = new OrderItem();

                    item.setProductId(itemDto.getProductId());
                    item.setQuantity(itemDto.getQuantity());
                    item.setPrice(itemDto.getPrice());

                    item.setOrder(order);

                    return item;

                })
                .collect(Collectors.toList());

        order.setItems(items);

        BigDecimal total = items.stream()
                .map(i ->
                        i.getPrice()
                                .multiply(BigDecimal.valueOf(i.getQuantity()))
                )
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        order.setTotalAmount(total);

        return order;
    }

    // ================= RESPONSE =================

    public OrderResponseDTO toResponse(Order order) {

        List<OrderItemResponseDTO> items =
                order.getItems()
                        .stream()
                        .map(item -> {

                            OrderItemResponseDTO dto =
                                    new OrderItemResponseDTO();

                            dto.setProductId(item.getProductId());
                            dto.setQuantity(item.getQuantity());
                            dto.setPrice(item.getPrice());

                            return dto;

                        })
                        .collect(Collectors.toList());

        return buildResponse(order, items);
    }

    private OrderResponseDTO buildResponse(
            Order order,
            List<OrderItemResponseDTO> items) {

        OrderResponseDTO response = new OrderResponseDTO();

        response.setId(order.getId());
        response.setOrderNumber(order.getOrderNumber());
        response.setUserId(order.getUserId());
        response.setTotalAmount(order.getTotalAmount());
        response.setStatus(order.getStatus());
        response.setPaymentType(order.getPaymentType());
        response.setFulfillmentType(order.getFulfillmentType());
        response.setItems(items);
        response.setCreatedAt(order.getCreatedAt());
        response.setUpdatedAt(order.getUpdatedAt());

        return response;
    }

    // ================= SAGA EVENT =================

    public OrderCreatedEvent toSagaEvent(
            Order order,
            String sagaId,
            String traceId) {

        List<OrderItemEvent> items = order.getItems()
                .stream()
                .map(i -> new OrderItemEvent(
                        i.getProductId(),
                        i.getQuantity(),
                        i.getPrice()
                ))
                .toList();

        OrderCreatedEvent event = new OrderCreatedEvent();

        // BaseEvent fields
        event.setSagaId(sagaId);
        event.setOrderId(order.getId());
        event.setTraceId(traceId);
        event.setTimestamp(Instant.now());
        event.setOrderNumber(order.getOrderNumber());


        // Business fields
        event.setUserId(order.getUserId());
        event.setTotalAmount(order.getTotalAmount());
        event.setItems(items);
        event.setCreatedAt(Instant.now());

        return event;
    }

    // ================= UTIL =================

    private String generateOrderNumber() {

        return "ORD-" + UUID.randomUUID()
                .toString()
                .substring(0, 8)
                .toUpperCase();
    }
}
