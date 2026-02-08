package com.deepana.inventoryservice.events;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderItemEvent {

    private Long productId;
    private Integer quantity;
    private BigDecimal price;
}
