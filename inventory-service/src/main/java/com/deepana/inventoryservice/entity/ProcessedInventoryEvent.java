package com.deepana.inventoryservice.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_inventory_events")
@Data
public class ProcessedInventoryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sagaId;

    private Long orderId;

    private String eventType;

    private LocalDateTime processedAt;
}