package com.deepana.inventoryservice;

import com.deepana.inventoryservice.entity.Inventory;
import com.deepana.inventoryservice.repository.InventoryRepository;
import com.deepana.inventoryservice.service.InventoryService;
import com.deepana.saga.commondto.inventory.ReserveInventoryCommand;
import com.deepana.saga.commondto.inventory.ReleaseInventoryCommand;
import com.deepana.saga.commondto.order.OrderItemEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
        ReserveInventoryCommand command = command(inventory.getProductId(), 2);
        inventoryService.processReserve(command);

        assertThat(inventoryRepository.findByProductId(8L))
                .hasValueSatisfying(updated -> assertThat(updated.getAvailableQty()).isEqualTo(1));
        assertThat(outboxCount("inventory.failed")).isEqualTo(1);
        assertThat(outboxCount("inventory.reserved")).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM processed_inventory_events
                WHERE saga_id = ? AND event_type = 'RESERVE_FAILED'
                """, Long.class, command.getSagaId())).isEqualTo(1);
    }

    @Test
    void multiItemReservationFailureRestoresEarlierStockAndCommitsFailure() throws Exception {
        inventory(13L, 5);
        inventory(14L, 0);
        ReserveInventoryCommand command = command(13L, 3);
        OrderItemEvent unavailableItem = new OrderItemEvent();
        unavailableItem.setProductId(14L);
        unavailableItem.setQuantity(1);
        unavailableItem.setPrice(BigDecimal.ONE);
        command.setItems(List.of(command.getItems().get(0), unavailableItem));

        inventoryService.processReserve(command);

        assertThat(inventoryRepository.findByProductId(13L))
                .hasValueSatisfying(updated -> {
                    assertThat(updated.getAvailableQty()).isEqualTo(5);
                    assertThat(updated.getReservedQty()).isZero();
                });
        assertThat(inventoryRepository.findByProductId(14L))
                .hasValueSatisfying(updated -> {
                    assertThat(updated.getAvailableQty()).isZero();
                    assertThat(updated.getReservedQty()).isZero();
                });
        assertThat(outboxCount("inventory.failed")).isEqualTo(1);
        assertThat(outboxCount("inventory.reserved")).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM processed_inventory_events
                WHERE saga_id = ? AND event_type = 'RESERVE_FAILED'
                """, Long.class, command.getSagaId())).isEqualTo(1);
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

    @Test
    void concurrentReservationsNeverOversellLimitedStock() throws Exception {
        inventory(12L, 10);
        int commandCount = 50;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(commandCount);
        AtomicInteger orderIds = new AtomicInteger(1_000);
        try {
            List<Future<?>> reservations = new ArrayList<>();
            for (int index = 0; index < commandCount; index++) {
                reservations.add(executor.submit(() -> {
                    ReserveInventoryCommand reserve = command(12L, 1);
                    reserve.setOrderId((long) orderIds.getAndIncrement());
                    await(start);
                    try {
                        inventoryService.processReserve(reserve);
                    } catch (JsonProcessingException exception) {
                        throw new IllegalStateException("Reservation command failed", exception);
                    }
                }));
            }
            start.countDown();
            for (Future<?> reservation : reservations) {
                reservation.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(inventoryRepository.findByProductId(12L))
                .hasValueSatisfying(updated -> {
                    assertThat(updated.getAvailableQty()).isZero();
                    assertThat(updated.getReservedQty()).isEqualTo(10);
                });
        assertThat(outboxCount("inventory.reserved")).isEqualTo(10);
        assertThat(outboxCount("inventory.failed")).isEqualTo(40);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM processed_inventory_events
                WHERE event_type = 'RESERVE'
                """, Long.class)).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM processed_inventory_events
                WHERE event_type = 'RESERVE_FAILED'
                """, Long.class)).isEqualTo(40);
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

    private void await(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Reservation concurrency test was interrupted", exception);
        }
    }
}
