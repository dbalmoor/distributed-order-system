package com.deepana.orderservice.mapper;

import com.deepana.orderservice.dto.request.CreateOrderRequestDTO;
import com.deepana.orderservice.dto.response.OrderItemResponseDTO;
import com.deepana.orderservice.dto.response.OrderResponseDTO;
import com.deepana.orderservice.entity.Order;
import com.deepana.orderservice.entity.OrderItem;
import com.deepana.orderservice.entity.OrderStatus;

import com.deepana.orderservice.events.OrderCreatedEvent;
import com.deepana.orderservice.events.OrderItemEvent;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class OrderMapper {

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

        return getOrderResponseDTO(order, items);
    }

    private static @NonNull OrderResponseDTO getOrderResponseDTO(Order order, List<OrderItemResponseDTO> items) {
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

    private String generateOrderNumber() {

        return "ORD-" + UUID.randomUUID()
                .toString()
                .substring(0, 8)
                .toUpperCase();
    }

    public OrderCreatedEvent mapToEvent(Order order) {

        List<OrderItemEvent> items = order.getItems().stream()
                .map(i -> new OrderItemEvent(
                        i.getProductId(),
                        i.getQuantity(),
                        i.getPrice()
                ))
                .toList();

        // ✅ Get traceId from MDC (or fallback)
        String traceId = MDC.get("traceId");

        if (traceId == null) {
            traceId = order.getOrderNumber(); // fallback safety
        }

        return new OrderCreatedEvent(
                order.getId(),
                order.getOrderNumber(),
                order.getUserId(),
                order.getTotalAmount(),
                items,
                order.getCreatedAt(),
                traceId // ✅ pass it
        );
    }


}
