package com.deepana.paymentservice.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payments_saga_id_type", columnNames = {"saga_id", "type"})
})
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;

    @Column(name = "saga_id", nullable = false, length = 36)
    private String sagaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private PaymentType type;

    private String orderNumber;

    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    private String status; // SUCCESS / FAILED

    private LocalDateTime createdAt;
}
