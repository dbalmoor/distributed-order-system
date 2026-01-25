package com.deepana.orderservice.dto.request;

import com.deepana.orderservice.entity.FulfillmentType;
import com.deepana.orderservice.entity.PaymentType;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Data
@Getter
@Setter
public class CreateOrderRequestDTO {

    private Long userId;

    private PaymentType paymentType;      // ONLINE / COD

    private FulfillmentType fulfillmentType;  // DELIVERY / PICKUP

    private List<OrderItemRequestDTO> items;
}
