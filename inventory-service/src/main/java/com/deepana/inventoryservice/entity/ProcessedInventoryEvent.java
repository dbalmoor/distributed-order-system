package com.deepana.inventoryservice.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_inventory_events", uniqueConstraints = {
        @UniqueConstraint(name = "uk_processed_inventory_events_saga_event",
                columnNames = {"saga_id", "event_type"})
})
@Data
public class ProcessedInventoryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "saga_id", nullable = false)
    private String sagaId;

    private Long orderId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    private LocalDateTime processedAt;
}