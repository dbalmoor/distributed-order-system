package com.deepana.orderservice.dto.request;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

@Data
@Getter
@Setter
public class OrderItemRequestDTO {

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull
    @Positive(message = "Quantity must be greater than 0")
    private Integer quantity;

    @NotNull
    @DecimalMin(value = "0.1", message = "Price must be greater than 0")
    private BigDecimal price;
}
