package com.deepana.orderservice;

import com.deepana.orderservice.entity.Order;
import com.deepana.orderservice.entity.OrderStatus;
import com.deepana.orderservice.kafka.OrderCommandConsumer;
import com.deepana.orderservice.repository.OrderRepository;
import com.deepana.saga.commondto.base.BaseEvent;
import com.deepana.saga.commondto.order.CancelOrderCommand;
import com.deepana.saga.commondto.order.ConfirmOrderCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@AutoConfigureMockMvc
class OrderStateMachineIntegrationTest extends IntegrationTestBase {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderCommandConsumer commandConsumer;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearOrders() {
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM orders");
    }

    @Test
    void confirmCommandCompletesAndPublishesEventAfterCommit() throws Exception {
        Order order = createOrder();
        try (KafkaConsumer<String, String> consumer = consumer("order.confirmed")) {
            commandConsumer.onConfirm(json(confirm(order)));

            ConsumerRecord<String, String> record = pollRecord(consumer);
            assertThat(record.key()).isEqualTo(order.getId().toString());
            BaseEvent event = objectMapper.readValue(record.value(), BaseEvent.class);
            assertThat(event.getOrderId()).isEqualTo(order.getId());
            assertThat(status(order)).isEqualTo(OrderStatus.COMPLETED);
        }
    }

    @Test
    void cancelCommandCancelsAndPublishesEventAfterCommit() throws Exception {
        Order order = createOrder();
        try (KafkaConsumer<String, String> consumer = consumer("order.cancelled")) {
            commandConsumer.onCancel(json(cancel(order)));

            ConsumerRecord<String, String> record = pollRecord(consumer);
            assertThat(record.key()).isEqualTo(order.getId().toString());
            assertThat(objectMapper.readValue(record.value(), BaseEvent.class).getOrderId())
                    .isEqualTo(order.getId());
            assertThat(status(order)).isEqualTo(OrderStatus.CANCELLED);
        }
    }

    @Test
    void duplicateTerminalCommandIsNoOpAndDoesNotRepublish() throws Exception {
        Order order = createOrder();
        try (KafkaConsumer<String, String> consumer = consumer("order.confirmed")) {
            ConfirmOrderCommand command = confirm(order);
            commandConsumer.onConfirm(json(command));
            assertThat(pollRecord(consumer)).isNotNull();

            commandConsumer.onConfirm(json(command));
            assertThat(consumer.poll(Duration.ofSeconds(1)).count()).isZero();
            assertThat(status(order)).isEqualTo(OrderStatus.COMPLETED);
        }
    }

    @Test
    void cancelAfterCompletedIsRejectedAndStatusRemainsCompleted() throws Exception {
        Order order = createOrder();
        commandConsumer.onConfirm(json(confirm(order)));

        assertThatThrownBy(() -> commandConsumer.onCancel(json(cancel(order))))
                .isInstanceOf(IllegalStateException.class);
        assertThat(status(order)).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void inventoryAndPaymentEventsDoNotChangeOrderStatus() throws Exception {
        Order order = createOrder();
        BaseEvent event = new BaseEvent();
        event.setOrderId(order.getId());
        event.setOrderNumber(order.getOrderNumber());
        String json = objectMapper.writeValueAsString(event);

        for (String topic : List.of(
                "inventory.reserved", "inventory.failed", "payment.success", "payment.failed")) {
            kafkaTemplate.send(topic, order.getId().toString(), json).get();
        }

        Thread.sleep(500);
        assertThat(status(order)).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    void restCancelReturnsAcceptedAndLeavesStatusCreated() throws Exception {
        Order order = createOrder();

        MvcResult result = mockMvc.perform(put("/orders/{id}/cancel", order.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(202);
        assertThat(status(order)).isEqualTo(OrderStatus.CREATED);
        assertThat(result.getResponse().getContentAsString()).contains("\"displayStatus\":\"PENDING\"");
    }

    @Test
    void restCancelForUnknownOrderReturnsNotFound() throws Exception {
        MvcResult result = mockMvc.perform(put("/orders/{id}/cancel", Long.MAX_VALUE)
                        .accept(MediaType.APPLICATION_JSON))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void flywayV3MigratesLegacyStatuses() throws Exception {
        String schema = "legacy_" + UUID.randomUUID().toString().replace("-", "");
        String schemaUrl = POSTGRES.getJdbcUrl() + "?currentSchema=" + schema;
        Flyway.configure()
                .dataSource(schemaUrl, POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .target(MigrationVersion.fromVersion("1"))
                .load()
                .migrate();

        try (var connection = java.sql.DriverManager.getConnection(
                schemaUrl,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
             var statement = connection.prepareStatement(
                     "INSERT INTO " + schema + ".orders (order_number, status, payment_pending) VALUES (?, ?, false)")) {
            for (String legacy : List.of(
                    "INVENTORY_RESERVED", "PAYMENT_SUCCESS_PENDING", "PAYMENT_FAILED_PENDING", "FAILED")) {
                statement.setString(1, "LEGACY-" + legacy);
                statement.setString(2, legacy);
                statement.addBatch();
            }
            statement.executeBatch();
        }

        Flyway.configure()
                .dataSource(schemaUrl, POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .load()
                .migrate();

        try (var connection = java.sql.DriverManager.getConnection(
                schemaUrl,
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
             var statement = connection.createStatement();
             var results = statement.executeQuery("SELECT order_number, status FROM " + schema + ".orders")) {
            java.util.Map<String, String> statuses = new java.util.HashMap<>();
            while (results.next()) {
                statuses.put(results.getString("order_number"), results.getString("status"));
            }
            assertThat(statuses).containsEntry("LEGACY-INVENTORY_RESERVED", "CREATED")
                    .containsEntry("LEGACY-PAYMENT_SUCCESS_PENDING", "CREATED")
                    .containsEntry("LEGACY-PAYMENT_FAILED_PENDING", "CREATED")
                    .containsEntry("LEGACY-FAILED", "CANCELLED");
        }
    }

    private Order createOrder() {
        Order order = new Order();
        order.setOrderNumber("ORD-" + UUID.randomUUID());
        order.setStatus(OrderStatus.CREATED);
        order.setItems(List.of());
        return orderRepository.saveAndFlush(order);
    }

    private OrderStatus status(Order order) {
        return new TransactionTemplate(transactionManager).execute(status ->
                orderRepository.findById(order.getId()).orElseThrow().getStatus());
    }

    private ConfirmOrderCommand confirm(Order order) {
        ConfirmOrderCommand command = new ConfirmOrderCommand();
        command.setOrderId(order.getId());
        command.setOrderNumber(order.getOrderNumber());
        command.setSagaId(UUID.randomUUID().toString());
        command.setTraceId("trace-" + order.getId());
        return command;
    }

    private CancelOrderCommand cancel(Order order) {
        CancelOrderCommand command = new CancelOrderCommand();
        command.setOrderId(order.getId());
        command.setOrderNumber(order.getOrderNumber());
        command.setSagaId(UUID.randomUUID().toString());
        command.setTraceId("trace-" + order.getId());
        command.setReason("test");
        return command;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private KafkaConsumer<String, String> consumer(String topic) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "order-phase2-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        try (AdminClient admin = AdminClient.create(Map.of(
                "bootstrap.servers", KAFKA.getBootstrapServers()))) {
            try {
                admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get();
            } catch (java.util.concurrent.ExecutionException e) {
                if (!(e.getCause() instanceof org.apache.kafka.common.errors.TopicExistsException)) {
                    throw new IllegalStateException("Could not create test topic " + topic, e);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while creating test topic " + topic, e);
        }

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
        throw new AssertionError("Timed out waiting for published Kafka event");
    }
}
