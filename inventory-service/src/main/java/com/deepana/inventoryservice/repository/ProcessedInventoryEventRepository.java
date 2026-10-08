package com.deepana.inventoryservice.repository;

import com.deepana.inventoryservice.entity.Inventory;
import com.deepana.inventoryservice.entity.ProcessedInventoryEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface ProcessedInventoryEventRepository extends JpaRepository<ProcessedInventoryEvent, Long> {

    boolean existsBySagaIdAndEventType(
            String sagaId,
            String eventType
    );

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO processed_inventory_events (saga_id, order_id, event_type, processed_at)
            VALUES (:sagaId, :orderId, :eventType, now())
            ON CONFLICT (saga_id, event_type) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("sagaId") String sagaId,
            @Param("orderId") Long orderId,
            @Param("eventType") String eventType);

}
