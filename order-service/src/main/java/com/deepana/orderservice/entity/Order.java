package com.deepana.orderservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonManagedReference;


@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Public Order Reference
    @Column(unique = true, nullable = false)
    private String orderNumber;

    private Long userId;

    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    private FulfillmentType fulfillmentType;

    @Version
    private Long version;

    // One Order → Many Items
    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @JsonManagedReference
    private List<OrderItem> items;


    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    void created() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = OrderStatus.CREATED;
        this.orderNumber = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @PreUpdate
    void updated() {
        this.updatedAt = LocalDateTime.now();
    }
}
