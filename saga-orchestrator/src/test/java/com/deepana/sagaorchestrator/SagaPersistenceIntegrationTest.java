package com.deepana.sagaorchestrator;

import com.deepana.saga.commondto.inventory.InventoryFailedEvent;
import com.deepana.saga.commondto.inventory.InventoryReservedEvent;
import com.deepana.saga.commondto.order.OrderCancelRequestedEvent;
import com.deepana.saga.commondto.order.OrderCancelledEvent;
import com.deepana.saga.commondto.order.OrderConfirmedEvent;
import com.deepana.saga.commondto.order.OrderCreatedEvent;
import com.deepana.saga.commondto.order.OrderItemEvent;
import com.deepana.saga.commondto.payment.PaymentFailedEvent;
import com.deepana.saga.commondto.payment.PaymentSuccessEvent;
import com.deepana.sagaorchestrator.outbox.OutboxPoller;
import com.deepana.sagaorchestrator.outbox.OutboxTransactions;
import com.deepana.sagaorchestrator.service.SagaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "outbox.poller.enabled=false")
class SagaPersistenceIntegrationTest extends IntegrationTestBase {

    @Autowired private SagaService sagaService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OutboxPoller outboxPoller;
    @Autowired private OutboxTransactions outboxTransactions;
    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void clearSagaData() {
        jdbcTemplate.update("DELETE FROM outbox");
        jdbcTemplate.update("DELETE FROM processed_message");
        jdbcTemplate.update("DELETE FROM saga_step_log");
        jdbcTemplate.update("DELETE FROM saga_instance");
    }

    @Test
    void happyPathCompletesOnlyAfterOrderConfirmed() {
        OrderCreatedEvent created = order(1001L);
        start(created);
        String sagaId = sagaId(created.getOrderId());

        sagaService.handleInventoryReserved(reserved(created, sagaId), messageId());
        assertState(created.getOrderId(), "ACTIVE", "CHARGE_PAYMENT");
        sagaService.handlePaymentSuccess(paymentSuccess(created, sagaId), messageId());
        assertState(created.getOrderId(), "ACTIVE", "CONFIRM_ORDER");
        assertThat(countOutbox("order.confirm.cmd")).isEqualTo(1);

        sagaService.handleOrderConfirmed(confirmed(created, sagaId), messageId());
        assertState(created.getOrderId(), "COMPLETED", "COMPLETED");
    }

    @Test
    void inventoryAndPaymentFailuresFinishCancelledOnlyAfterCancelAcknowledgment() {
        OrderCreatedEvent inventoryFailure = order(1002L);
        start(inventoryFailure);
        String inventorySaga = sagaId(inventoryFailure.getOrderId());
        sagaService.handleInventoryFailed(inventoryFailed(inventoryFailure, inventorySaga), messageId());
        assertState(inventoryFailure.getOrderId(), "COMPENSATING", "CANCEL_ORDER");
        sagaService.handleOrderCancelled(cancelled(inventoryFailure, inventorySaga), messageId());
        assertState(inventoryFailure.getOrderId(), "CANCELLED", "CANCELLED");

        OrderCreatedEvent paymentFailure = order(1003L);
        start(paymentFailure);
        String paymentSaga = sagaId(paymentFailure.getOrderId());
        sagaService.handleInventoryReserved(reserved(paymentFailure, paymentSaga), messageId());
        sagaService.handlePaymentFailed(paymentFailed(paymentFailure, paymentSaga), messageId());
        assertState(paymentFailure.getOrderId(), "COMPENSATING", "CANCEL_ORDER");
        sagaService.handleOrderCancelled(cancelled(paymentFailure, paymentSaga), messageId());
        assertState(paymentFailure.getOrderId(), "CANCELLED", "CANCELLED");
    }

    @Test
    void duplicateOrderCreatedAndMessageIdDoNotCreateAnotherSagaOrCommand() {
        OrderCreatedEvent event = order(1004L);
        String id = messageId();
        sagaService.handleOrderCreated(event, id);
        sagaService.handleOrderCreated(event, id);
        sagaService.handleOrderCreated(event, messageId());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM saga_instance WHERE order_id = ?", Long.class, event.getOrderId()))
                .isEqualTo(1);
        assertThat(countOutbox("inventory.reserve.cmd")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM processed_message WHERE consumer_name = 'saga-orchestrator:order.created'",
                Long.class)).isEqualTo(2);
    }

    @Test
    void outOfOrderEventLeavesSagaAndStepLogUnchanged() {
        OrderCreatedEvent created = order(1005L);
        start(created);
        String sagaId = sagaId(created.getOrderId());

        sagaService.handlePaymentSuccess(paymentSuccess(created, sagaId), messageId());

        assertState(created.getOrderId(), "ACTIVE", "RESERVE_INVENTORY");
        assertThat(countStepLog(created.getOrderId())).isEqualTo(1);
        assertThat(countOutbox("payment.charge.cmd")).isZero();
    }

    @Test
    void cancelRequestsAreAcceptedOnlyBeforeChargeAndBeforePivot() {
        OrderCreatedEvent beforeCharge = order(1006L);
        start(beforeCharge);
        sagaService.handleCancelRequested(cancelRequest(beforeCharge), messageId());
        assertState(beforeCharge.getOrderId(), "COMPENSATING", "CANCEL_ORDER");
        assertThat(countOutbox("order.cancel.cmd")).isEqualTo(1);

        OrderCreatedEvent duringCharge = order(1007L);
        start(duringCharge);
        String chargeSaga = sagaId(duringCharge.getOrderId());
        sagaService.handleInventoryReserved(reserved(duringCharge, chargeSaga), messageId());
        sagaService.handleCancelRequested(cancelRequest(duringCharge), messageId());
        assertState(duringCharge.getOrderId(), "ACTIVE", "CHARGE_PAYMENT");
        assertThat(countOutbox("order.cancel.cmd")).isEqualTo(1);

        OrderCreatedEvent afterPivot = order(1008L);
        start(afterPivot);
        String pivotSaga = sagaId(afterPivot.getOrderId());
        sagaService.handleInventoryReserved(reserved(afterPivot, pivotSaga), messageId());
        sagaService.handlePaymentSuccess(paymentSuccess(afterPivot, pivotSaga), messageId());
        sagaService.handleCancelRequested(cancelRequest(afterPivot), messageId());
        assertState(afterPivot.getOrderId(), "ACTIVE", "CONFIRM_ORDER");
        assertThat(countOutbox("order.cancel.cmd")).isEqualTo(1);
    }

    @Test
    void latePaymentSuccessIsLoggedWithoutChangingStateOrIssuingRefund() {
        OrderCreatedEvent created = order(1009L);
        start(created);
        String sagaId = sagaId(created.getOrderId());
        sagaService.handleInventoryFailed(inventoryFailed(created, sagaId), messageId());
        sagaService.handlePaymentSuccess(paymentSuccess(created, sagaId), messageId());

        assertState(created.getOrderId(), "COMPENSATING", "CANCEL_ORDER");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM saga_step_log
                WHERE saga_id = ? AND event_type = 'LATE_SUCCESS'
                """, Long.class, sagaId)).isEqualTo(1);
        assertThat(countOutbox("payment.refund.cmd")).isZero();

        sagaService.handleOrderCancelled(cancelled(created, sagaId), messageId());
        sagaService.handlePaymentSuccess(paymentSuccess(created, sagaId), messageId());
        assertState(created.getOrderId(), "CANCELLED", "CANCELLED");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM saga_step_log
                WHERE saga_id = ? AND event_type = 'LATE_SUCCESS'
                """, Long.class, sagaId)).isEqualTo(2);
    }

    @Test
    void concurrentEventsForOneSagaSerializeThroughTheSagaRowLock() throws Exception {
        OrderCreatedEvent created = order(1010L);
        start(created);
        String sagaId = sagaId(created.getOrderId());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> reserved = executor.submit(() -> runAfter(start, () ->
                    sagaService.handleInventoryReserved(reserved(created, sagaId), messageId())));
            Future<?> failed = executor.submit(() -> runAfter(start, () ->
                    sagaService.handleInventoryFailed(inventoryFailed(created, sagaId), messageId())));
            start.countDown();
            reserved.get(30, TimeUnit.SECONDS);
            failed.get(30, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        String status = stateValue(created.getOrderId(), "status");
        String step = stateValue(created.getOrderId(), "current_step");
        assertThat(List.of("ACTIVE/CHARGE_PAYMENT", "COMPENSATING/CANCEL_ORDER"))
                .contains(status + "/" + step);
        assertThat(countStepLog(created.getOrderId())).isEqualTo(2);
        assertThat(countOutbox(null)).isEqualTo(2);
    }

    @Test
    void committedSagaResumesPublishingAfterPollerRestart() throws Exception {
        OrderCreatedEvent created = order(1011L);
        start(created);
        try (KafkaConsumer<String, String> consumer = consumer("inventory.reserve.cmd")) {
            outboxPoller.pollOnce();
            ConsumerRecord<String, String> record = pollRecord(consumer);
            assertThat(record.key()).isEqualTo(created.getOrderId().toString());
            assertThat(new String(record.headers().lastHeader("sagaId").value(), StandardCharsets.UTF_8))
                    .isEqualTo(sagaId(created.getOrderId()));
            assertThat(record.headers().lastHeader("messageId")).isNotNull();

            new OutboxPoller(outboxTransactions, kafkaTemplate, objectMapper, false, 50, 30_000).pollOnce();
            assertThat(consumer.poll(Duration.ofSeconds(1))).isEmpty();
        }
    }

    private void start(OrderCreatedEvent event) {
        sagaService.handleOrderCreated(event, messageId());
    }

    private OrderCreatedEvent order(Long orderId) {
        OrderCreatedEvent event = new OrderCreatedEvent();
        event.setOrderId(orderId);
        event.setOrderNumber("ORD-" + orderId);
        event.setSagaId(UUID.randomUUID().toString());
        event.setTraceId("trace-" + orderId);
        event.setTimestamp(Instant.now());
        event.setTotalAmount(new BigDecimal("18.75"));
        OrderItemEvent item = new OrderItemEvent();
        item.setProductId(orderId + 5000L);
        item.setQuantity(2);
        item.setPrice(new BigDecimal("9.375"));
        event.setItems(List.of(item));
        return event;
    }

    private InventoryReservedEvent reserved(OrderCreatedEvent source, String sagaId) {
        InventoryReservedEvent event = new InventoryReservedEvent();
        copy(source, event, sagaId);
        event.setTotalAmount(source.getTotalAmount());
        event.setItems(source.getItems());
        return event;
    }

    private InventoryFailedEvent inventoryFailed(OrderCreatedEvent source, String sagaId) {
        InventoryFailedEvent event = new InventoryFailedEvent();
        copy(source, event, sagaId);
        event.setReason("no stock");
        return event;
    }

    private PaymentSuccessEvent paymentSuccess(OrderCreatedEvent source, String sagaId) {
        PaymentSuccessEvent event = new PaymentSuccessEvent();
        copy(source, event, sagaId);
        event.setTotalAmount(source.getTotalAmount());
        event.setItems(source.getItems());
        return event;
    }

    private PaymentFailedEvent paymentFailed(OrderCreatedEvent source, String sagaId) {
        PaymentFailedEvent event = new PaymentFailedEvent();
        copy(source, event, sagaId);
        event.setReason("declined");
        return event;
    }

    private OrderConfirmedEvent confirmed(OrderCreatedEvent source, String sagaId) {
        OrderConfirmedEvent event = new OrderConfirmedEvent();
        copy(source, event, sagaId);
        return event;
    }

    private OrderCancelledEvent cancelled(OrderCreatedEvent source, String sagaId) {
        OrderCancelledEvent event = new OrderCancelledEvent();
        copy(source, event, sagaId);
        return event;
    }

    private OrderCancelRequestedEvent cancelRequest(OrderCreatedEvent source) {
        OrderCancelRequestedEvent event = new OrderCancelRequestedEvent();
        copy(source, event, null);
        return event;
    }

    private void copy(OrderCreatedEvent source, com.deepana.saga.commondto.base.BaseEvent target,
                      String sagaId) {
        target.setOrderId(source.getOrderId());
        target.setOrderNumber(source.getOrderNumber());
        target.setTraceId(source.getTraceId());
        target.setTimestamp(Instant.now());
        target.setSagaId(sagaId);
    }

    private String sagaId(Long orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT saga_id FROM saga_instance WHERE order_id = ?", String.class, orderId);
    }

    private void assertState(Long orderId, String status, String step) {
        assertThat(stateValue(orderId, "status") + "/" + stateValue(orderId, "current_step"))
                .isEqualTo(status + "/" + step);
    }

    private String stateValue(Long orderId, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM saga_instance WHERE order_id = ?", String.class, orderId);
    }

    private long countStepLog(Long orderId) {
        return jdbcTemplate.queryForObject("""
                SELECT count(*) FROM saga_step_log
                WHERE saga_id = (SELECT saga_id FROM saga_instance WHERE order_id = ?)
                """, Long.class, orderId);
    }

    private long countOutbox(String topic) {
        if (topic == null) {
            return jdbcTemplate.queryForObject("SELECT count(*) FROM outbox", Long.class);
        }
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbox WHERE topic = ?", Long.class, topic);
    }

    private String messageId() {
        return UUID.randomUUID().toString();
    }

    private KafkaConsumer<String, String> consumer(String topic) throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            try {
                admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get();
            } catch (java.util.concurrent.ExecutionException e) {
                if (!(e.getCause() instanceof TopicExistsException)) {
                    throw e;
                }
            }
        }
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "saga-persistence-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties);
        consumer.subscribe(List.of(topic));
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (consumer.assignment().isEmpty() && System.nanoTime() < deadline) {
            consumer.poll(Duration.ofMillis(100));
        }
        assertThat(consumer.assignment()).isNotEmpty();
        return consumer;
    }

    private ConsumerRecord<String, String> pollRecord(KafkaConsumer<String, String> consumer) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            var records = consumer.poll(Duration.ofMillis(200));
            if (!records.isEmpty()) {
                return records.iterator().next();
            }
        }
        throw new AssertionError("No saga command was published before timeout");
    }

    private void runAfter(CountDownLatch start, Runnable action) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start concurrent saga handlers");
            }
            action.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent saga test interrupted", e);
        }
    }
}
