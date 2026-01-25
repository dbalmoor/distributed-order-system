package com.deepana.orderservice.dto.response;

import com.deepana.orderservice.entity.FulfillmentType;
import com.deepana.orderservice.entity.OrderStatus;
import com.deepana.orderservice.entity.PaymentType;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class OrderResponseDTO {

    private Long id;

    private String orderNumber;

    private Long userId;

    private BigDecimal totalAmount;

    private OrderStatus status;

    private PaymentType paymentType;

    private FulfillmentType fulfillmentType;

    private List<OrderItemResponseDTO> items;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
