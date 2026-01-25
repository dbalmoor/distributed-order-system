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
public class CreateOrderRequest {

    private Long userId;

    private PaymentType paymentType;

    private FulfillmentType fulfillmentType;

    private List<OrderItemRequest> items;
}
