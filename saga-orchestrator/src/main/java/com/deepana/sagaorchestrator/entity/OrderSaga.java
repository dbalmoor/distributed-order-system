package com.deepana.sagaorchestrator.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "order_saga")
@Data
public class OrderSaga {

    @Id
    private Long sagaId;

    private Long orderId;

    @Enumerated(EnumType.STRING)
    private SagaStatus status;

    @Enumerated(EnumType.STRING)
    private SagaStep step;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

