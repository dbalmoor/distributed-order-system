package com.deepana.inventoryservice.repository;

import com.deepana.inventoryservice.entity.Inventory;
import com.deepana.inventoryservice.entity.ProcessedInventoryEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ProcessedInventoryEventRepository extends JpaRepository<ProcessedInventoryEvent, Long> {

    boolean existsBySagaIdAndEventType(
            String sagaId,
            String eventType
    );

}
