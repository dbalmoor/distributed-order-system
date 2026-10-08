package com.deepana.paymentservice.repository;

import com.deepana.paymentservice.entity.Payment;
import com.deepana.paymentservice.entity.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO payments (saga_id, type, order_id, order_number, amount, status, created_at)
            VALUES (:sagaId, :type, :orderId, :orderNumber, :amount, :status, :createdAt)
            ON CONFLICT (saga_id, type) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("sagaId") String sagaId,
            @Param("type") String type,
            @Param("orderId") Long orderId,
            @Param("orderNumber") String orderNumber,
            @Param("amount") BigDecimal amount,
            @Param("status") String status,
            @Param("createdAt") LocalDateTime createdAt);

    Optional<Payment> findBySagaIdAndType(String sagaId, PaymentType type);
}
