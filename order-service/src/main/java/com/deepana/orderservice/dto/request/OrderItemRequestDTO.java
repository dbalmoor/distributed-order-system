package com.deepana.orderservice.dto.request;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Data
@Getter
@Setter
public class OrderItemRequestDTO {

    private Long productId;
    private Integer quantity;
    private BigDecimal price;
}
