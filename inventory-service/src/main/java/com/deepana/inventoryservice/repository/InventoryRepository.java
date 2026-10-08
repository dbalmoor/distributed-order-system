package com.deepana.inventoryservice.repository;

import com.deepana.inventoryservice.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface InventoryRepository
        extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductId(Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.productId = :productId")
    Optional<Inventory> findByProductIdForUpdate(Long productId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(value = """
            UPDATE inventory
            SET available_qty = available_qty + :quantity,
                reserved_qty = reserved_qty - :quantity,
                version = COALESCE(version, 0) + 1
            WHERE product_id = :productId
              AND reserved_qty >= :quantity
            """, nativeQuery = true)
    int releaseReservation(
            @Param("productId") Long productId,
            @Param("quantity") Integer quantity);
}
