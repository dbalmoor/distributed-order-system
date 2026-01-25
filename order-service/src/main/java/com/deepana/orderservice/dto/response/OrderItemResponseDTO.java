package com.deepana.orderservice.dto.response;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Data
@Getter
@Setter
public class OrderItemResponseDTO {

    private Long productId;
    private Integer quantity;
    private BigDecimal price;
}
