package com.deepana.inventoryservice;

import com.deepana.inventoryservice.entity.Inventory;
import com.deepana.inventoryservice.repository.InventoryRepository;
import com.deepana.inventoryservice.service.InventoryService;
import com.deepana.saga.commondto.inventory.ReserveInventoryCommand;
import com.deepana.saga.commondto.inventory.ReleaseInventoryCommand;
import com.deepana.saga.commondto.order.OrderItemEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "outbox.poller.enabled=false")
class InventoryOutboxIntegrationTest extends IntegrationTestBase {

    @Autowired private InventoryService inventoryService;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM outbox");
        jdbcTemplate.update("DELETE FROM processed_inventory_events");
        inventoryRepository.deleteAllInBatch();
    }

    @Test
    void successfulReservationCommitsItsOutboxEventWithTheStockMutation() throws Exception {
        Inventory inventory = inventory(7L, 8);
        ReserveInventoryCommand command = command(inventory.getProductId(), 3);

        inventoryService.processReserve(command);

        assertThat(inventoryRepository.findByProductId(7L))
                .hasValueSatisfying(updated -> assertThat(updated.getAvailableQty()).isEqualTo(5));
        assertThat(outboxCount("inventory.reserved")).isEqualTo(1);
    }

    @Test
    void insufficientStockCommitsFailureEventWithoutChangingInventory() throws Exception {
        Inventory inventory = inventory(8L, 1);
        inventoryService.processReserve(command(inventory.getProductId(), 2));

        assertThat(inventoryRepository.findByProductId(8L))
                .hasValueSatisfying(updated -> assertThat(updated.getAvailableQty()).isEqualTo(1));
        assertThat(outboxCount("inventory.failed")).isEqualTo(1);
        assertThat(outboxCount("inventory.reserved")).isZero();
    }

    @Test
    void rollingBackReservationAlsoRollsBackTheOutboxRow() throws Exception {
        Inventory inventory = inventory(9L, 8);
        ReserveInventoryCommand command = command(inventory.getProductId(), 3);

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status -> {
            try {
                inventoryService.processReserve(command);
            } catch (Exception e) {
                throw new IllegalStateException("Reservation unexpectedly failed", e);
            }
            throw new IllegalStateException("force transaction rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(inventoryRepository.findByProductId(9L))
                .hasValueSatisfying(updated -> assertThat(updated.getAvailableQty()).isEqualTo(8));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM processed_inventory_events", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM outbox", Long.class)).isZero();
    }

    @Test
    void releaseRestoresStockOnceAndDuplicateReleaseReemitsAcknowledgment() throws Exception {
        Inventory inventory = inventory(10L, 8);
        ReserveInventoryCommand reserve = command(inventory.getProductId(), 3);
        inventoryService.processReserve(reserve);

        ReleaseInventoryCommand release = releaseCommand(reserve);
        inventoryService.processRelease(release);
        inventoryService.processRelease(release);

        assertThat(inventoryRepository.findByProductId(10L))
                .hasValueSatisfying(updated -> {
                    assertThat(updated.getAvailableQty()).isEqualTo(8);
                    assertThat(updated.getReservedQty()).isZero();
                });
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM processed_inventory_events
                WHERE saga_id = ? AND event_type = 'RELEASED'
                """, Long.class, reserve.getSagaId())).isEqualTo(1);
        assertThat(outboxCount("inventory.released")).isEqualTo(2);
    }

    @Test
    void releaseBeforeReserveWritesMarkerAndPreventsLaterStockHold() throws Exception {
        Inventory inventory = inventory(11L, 8);
        ReserveInventoryCommand reserve = command(inventory.getProductId(), 3);

        inventoryService.processRelease(releaseCommand(reserve));
        inventoryService.processReserve(reserve);

        assertThat(inventoryRepository.findByProductId(11L))
                .hasValueSatisfying(updated -> {
                    assertThat(updated.getAvailableQty()).isEqualTo(8);
                    assertThat(updated.getReservedQty()).isZero();
                });
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM processed_inventory_events
                WHERE saga_id = ? AND event_type = 'RELEASED'
                """, Long.class, reserve.getSagaId())).isEqualTo(1);
        assertThat(outboxCount("inventory.released")).isEqualTo(1);
        assertThat(outboxCount("inventory.failed")).isEqualTo(1);
        assertThat(outboxCount("inventory.reserved")).isZero();
    }

    private Inventory inventory(Long productId, int available) {
        Inventory inventory = new Inventory();
        inventory.setProductId(productId);
        inventory.setAvailableQty(available);
        inventory.setReservedQty(0);
        return inventoryRepository.saveAndFlush(inventory);
    }

    private ReserveInventoryCommand command(Long productId, int quantity) {
        ReserveInventoryCommand command = new ReserveInventoryCommand();
        command.setSagaId(UUID.randomUUID().toString());
        command.setOrderId(productId + 100L);
        command.setOrderNumber("ORD-" + productId);
        command.setTraceId("trace-" + productId);
        command.setTotalAmount(BigDecimal.TEN);
        OrderItemEvent item = new OrderItemEvent();
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setPrice(BigDecimal.ONE);
        command.setItems(List.of(item));
        return command;
    }

    private ReleaseInventoryCommand releaseCommand(ReserveInventoryCommand source) {
        ReleaseInventoryCommand command = new ReleaseInventoryCommand();
        command.setSagaId(source.getSagaId());
        command.setOrderId(source.getOrderId());
        command.setOrderNumber(source.getOrderNumber());
        command.setTraceId(source.getTraceId());
        command.setTimestamp(source.getTimestamp());
        command.setItems(source.getItems());
        return command;
    }

    private long outboxCount(String topic) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox WHERE topic = ?", Long.class, topic);
    }
}
