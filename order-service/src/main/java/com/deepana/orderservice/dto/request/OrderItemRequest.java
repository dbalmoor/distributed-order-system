package com.deepana.orderservice.dto.request;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class OrderItemRequest {

    private Long productId;

    private Integer quantity;

    private Double price;
}
