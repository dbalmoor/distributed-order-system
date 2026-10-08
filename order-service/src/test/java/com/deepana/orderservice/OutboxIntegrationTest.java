package com.deepana.orderservice;

import com.deepana.orderservice.entity.Order;
import com.deepana.orderservice.outbox.OutboxPoller;
import com.deepana.orderservice.outbox.OutboxTransactions;
import com.deepana.orderservice.outbox.OutboxWriter;
import com.deepana.orderservice.repository.OrderRepository;
import com.deepana.saga.commondto.base.BaseEvent;
import com.fasterxml.jackson.databind.JsonNode;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "outbox.poller.enabled=false")
class OutboxIntegrationTest extends IntegrationTestBase {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OutboxWriter outboxWriter;
    @Autowired private OutboxTransactions outboxTransactions;
    @Autowired private OutboxPoller outboxPoller;
    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM outbox");
    }

    @Test
    void rolledBackBusinessWriteLeavesNoOutboxRow() {
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).execute(status -> {
            Order order = orderRepository.saveAndFlush(new Order());
            outboxWriter.write("ORDER", "order.created", "order.created", event(order.getId()));
            throw new IllegalStateException("force transaction rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(orderRepository.count()).isZero();
        assertThat(outboxCount()).isZero();
    }

    @Test
    void committedBusinessWriteCreatesOneMessageWithUniqueIdAndHeaders() throws Exception {
        Long orderId = new TransactionTemplate(transactionManager).execute(status -> {
            Order order = orderRepository.saveAndFlush(new Order());
            outboxWriter.write("ORDER", "order.created", "order.created", event(order.getId()));
            return order.getId();
        });

        assertThat(outboxCount()).isEqualTo(1);
        String messageId = jdbcTemplate.queryForObject(
                "SELECT message_id::text FROM outbox WHERE aggregate_id = ?",
                String.class, orderId.toString());
        assertThat(UUID.fromString(messageId)).isNotNull();
        JsonNode headers = objectMapper.readTree(jdbcTemplate.queryForObject(
                "SELECT headers::text FROM outbox WHERE message_id = CAST(? AS uuid)",
                String.class, messageId));
        assertThat(headers.get("messageId").asText()).isEqualTo(messageId);
        assertThat(headers.get("orderId").asText()).isEqualTo(orderId.toString());
        assertThat(headers.get("eventType").asText()).isEqualTo("order.created");
    }

    @Test
    void committedMessageSurvivesPollerRestartAndPublishesOnce() throws Exception {
        String topic = topicName();
        Long orderId = 502L;
        insertOutbox(topic, orderId);
        try (KafkaConsumer<String, String> consumer = consumer(topic)) {
            outboxPoller.pollOnce();
            ConsumerRecord<String, String> first = pollRecord(consumer);
            assertThat(first.key()).isEqualTo(orderId.toString());
            assertThat(first.headers().lastHeader("messageId")).isNotNull();

            OutboxPoller restartedPoller = new OutboxPoller(
                    outboxTransactions, kafkaTemplate, objectMapper, false, 50, 30_000);
            restartedPoller.pollOnce();
            assertThat(consumer.poll(Duration.ofSeconds(1))).isEmpty();
        }
    }

    @Test
    void concurrentPollersClaimAndPublishAnOutboxRowOnlyOnce() throws Exception {
        String topic = topicName();
        Long orderId = 503L;
        insertOutbox(topic, orderId);
        try (KafkaConsumer<String, String> consumer = consumer(topic)) {
            OutboxPoller secondInstance = new OutboxPoller(
                    outboxTransactions, kafkaTemplate, objectMapper, false, 50, 30_000);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<?> first = executor.submit(() -> runAfter(start, outboxPoller));
                Future<?> second = executor.submit(() -> runAfter(start, secondInstance));
                start.countDown();
                first.get(20, TimeUnit.SECONDS);
                second.get(20, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }

            assertThat(pollRecord(consumer).key()).isEqualTo(orderId.toString());
            assertThat(consumer.poll(Duration.ofSeconds(1))).isEmpty();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM outbox WHERE aggregate_id = ?",
                    String.class, orderId.toString())).isEqualTo("PUBLISHED");
        }
    }

    @Test
    void failedOldestRowBlocksItsAggregateAndExpiredLeaseCanBeReclaimed() throws Exception {
        String topic = topicName();
        UUID failedId = insertOutbox(topic, 601L);
        UUID blockedId = insertOutbox(topic, 601L);
        Long otherOrderId = 602L;
        UUID otherId = insertOutbox(topic, otherOrderId);
        jdbcTemplate.update("UPDATE outbox SET headers = '[]'::jsonb WHERE message_id = ?", failedId);

        try (KafkaConsumer<String, String> consumer = consumer(topic)) {
            outboxPoller.pollOnce();
            assertThat(pollRecord(consumer).key()).isEqualTo(otherOrderId.toString());
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM outbox WHERE message_id = ?", String.class, failedId))
                    .isEqualTo("FAILED");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM outbox WHERE message_id = ?", String.class, blockedId))
                    .isEqualTo("NEW");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM outbox WHERE message_id = ?", String.class, otherId))
                    .isEqualTo("PUBLISHED");

            UUID expiredId = insertOutbox(topic, 603L);
            jdbcTemplate.update("""
                    UPDATE outbox SET status = 'IN_PROGRESS',
                        lease_until = now() - interval '1 second'
                    WHERE message_id = ?
                    """, expiredId);
            outboxPoller.pollOnce();
            assertThat(pollRecord(consumer).key()).isEqualTo("603");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM outbox WHERE message_id = ?", String.class, expiredId))
                    .isEqualTo("PUBLISHED");
        }
    }

    private UUID insertOutbox(String topic, Long orderId) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            return outboxWriter.write("ORDER", topic, topic, event(orderId));
        });
    }

    private long outboxCount() {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM outbox", Long.class);
    }

    private BaseEvent event(Long orderId) {
        BaseEvent event = new BaseEvent();
        event.setSagaId(UUID.randomUUID().toString());
        event.setOrderId(orderId);
        event.setTraceId("trace-" + orderId);
        event.setOrderNumber("ORD-" + orderId);
        return event;
    }

    private String topicName() {
        return "outbox-test-" + UUID.randomUUID().toString().replace("-", "");
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
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "outbox-test-" + UUID.randomUUID());
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
        throw new AssertionError("No outbox message was published before timeout");
    }

    private void runAfter(CountDownLatch start, OutboxPoller poller) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to start concurrent pollers");
            }
            poller.pollOnce();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent poller test interrupted", e);
        }
    }
}
