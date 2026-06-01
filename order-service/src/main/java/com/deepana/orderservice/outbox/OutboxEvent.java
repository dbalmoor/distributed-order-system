package com.deepana.orderservice.outbox;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String aggregateType;   // ORDER
    private String aggregateId;     // orderId

    private String eventType;       // order.created

    @Column(columnDefinition = "TEXT")
    private String payload;         // JSON event

    private String sagaId;
    private String traceId;

    private String status;          // NEW, SENT

    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
}

