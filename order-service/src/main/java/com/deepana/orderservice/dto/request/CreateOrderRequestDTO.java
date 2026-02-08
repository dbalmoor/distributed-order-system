package com.deepana.orderservice.dto.request;

import com.deepana.orderservice.entity.FulfillmentType;
import com.deepana.orderservice.entity.PaymentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Data
@Getter
@Setter
public class CreateOrderRequestDTO {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Payment type is required")
    private PaymentType paymentType;      // ONLINE / COD

    @NotNull(message = "Fulfillment type is required")
    private FulfillmentType fulfillmentType;  // DELIVERY / PICKUP

    @NotEmpty(message = "Order must have at least one item")
    @Valid
    private List<OrderItemRequestDTO> items;
}
