package com.deepana.inventoryservice.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_orders")
@Data
public class ProcessedOrder {

    @Id
    private Long orderId;

    private String orderNumber;

    private LocalDateTime processedAt;
}

