package com.deepana.paymentservice.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;

    private String orderNumber;

    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    private String status; // SUCCESS / FAILED

    private LocalDateTime createdAt;
}
