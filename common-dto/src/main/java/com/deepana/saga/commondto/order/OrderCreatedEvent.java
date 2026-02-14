package com.deepana.saga.commondto.order;

import com.deepana.saga.commondto.base.BaseEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor   // Needed for Kafka/Jackson
public class OrderCreatedEvent extends BaseEvent {

    private Long userId;

    private BigDecimal totalAmount;

    private List<OrderItemEvent> items;

    private Instant createdAt;
}
