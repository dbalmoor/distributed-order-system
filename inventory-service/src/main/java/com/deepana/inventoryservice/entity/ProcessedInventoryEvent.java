package com.deepana.inventoryservice.entity;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProcessedInventoryEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sagaId;

    private Long orderId;

    private String eventType; // RESERVE or RELEASE

    private LocalDateTime processedAt;
}
