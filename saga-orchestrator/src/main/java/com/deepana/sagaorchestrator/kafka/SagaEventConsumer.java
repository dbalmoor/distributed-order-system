package com.deepana.sagaorchestrator.kafka;


import com.deepana.saga.commondto.inventory.InventoryFailedEvent;
import com.deepana.saga.commondto.inventory.InventoryReservedEvent;
import com.deepana.saga.commondto.inventory.InventoryReleasedEvent;
import com.deepana.saga.commondto.order.OrderCreatedEvent;
import com.deepana.saga.commondto.order.OrderCancelRequestedEvent;
import com.deepana.saga.commondto.order.OrderCancelledEvent;
import com.deepana.saga.commondto.order.OrderConfirmedEvent;
import com.deepana.saga.commondto.payment.PaymentFailedEvent;
import com.deepana.saga.commondto.payment.PaymentSuccessEvent;
import com.deepana.saga.commondto.payment.PaymentRefundedEvent;
import com.deepana.sagaorchestrator.service.SagaService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.errors.RetriableException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.stereotype.Component;
import org.springframework.retry.annotation.Backoff;

import java.net.ConnectException;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.net.SocketTimeoutException;
import java.sql.SQLTransientException;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
@Slf4j
public class SagaEventConsumer {

    private final SagaService sagaService;
    private final ObjectMapper objectMapper;

    // ---------------- ORDER ----------------

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delayExpression = "${kafka.retry.delay-ms:1000}",
                    multiplierExpression = "${kafka.retry.multiplier:2.0}",
                    maxDelayExpression = "${kafka.retry.max-delay-ms:10000}"),
            include = {TransientDataAccessException.class, DataAccessResourceFailureException.class,
                    RetriableException.class, SQLTransientException.class, ConnectException.class,
                    SocketException.class, SocketTimeoutException.class, TimeoutException.class},
            traversingCauses = "true",
            autoCreateTopics = "false",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(
            topics = "order.created",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderCreated(ConsumerRecord<String, String> record) throws JsonProcessingException {
        OrderCreatedEvent event = objectMapper.readValue(record.value(), OrderCreatedEvent.class);
        sagaService.handleOrderCreated(event, messageId(record));
    }

    // ---------------- INVENTORY ----------------

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delayExpression = "${kafka.retry.delay-ms:1000}",
                    multiplierExpression = "${kafka.retry.multiplier:2.0}",
                    maxDelayExpression = "${kafka.retry.max-delay-ms:10000}"),
            include = {TransientDataAccessException.class, DataAccessResourceFailureException.class,
                    RetriableException.class, SQLTransientException.class, ConnectException.class,
                    SocketException.class, SocketTimeoutException.class, TimeoutException.class},
            traversingCauses = "true",
            autoCreateTopics = "false",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(
            topics = "inventory.reserved",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onInventoryReserved(ConsumerRecord<String, String> record) throws JsonProcessingException {
        InventoryReservedEvent event =
                objectMapper.readValue(record.value(), InventoryReservedEvent.class);
        sagaService.handleInventoryReserved(event, messageId(record));
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delayExpression = "${kafka.retry.delay-ms:1000}",
                    multiplierExpression = "${kafka.retry.multiplier:2.0}",
                    maxDelayExpression = "${kafka.retry.max-delay-ms:10000}"),
            include = {TransientDataAccessException.class, DataAccessResourceFailureException.class,
                    RetriableException.class, SQLTransientException.class, ConnectException.class,
                    SocketException.class, SocketTimeoutException.class, TimeoutException.class},
            traversingCauses = "true",
            autoCreateTopics = "false",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(
            topics = "inventory.failed",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onInventoryFailed(ConsumerRecord<String, String> record) throws JsonProcessingException {
        InventoryFailedEvent event =
                objectMapper.readValue(record.value(), InventoryFailedEvent.class);
        sagaService.handleInventoryFailed(event, messageId(record));
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delayExpression = "${kafka.retry.delay-ms:1000}",
                    multiplierExpression = "${kafka.retry.multiplier:2.0}",
                    maxDelayExpression = "${kafka.retry.max-delay-ms:10000}"),
            include = {TransientDataAccessException.class, DataAccessResourceFailureException.class,
                    RetriableException.class, SQLTransientException.class, ConnectException.class,
                    SocketException.class, SocketTimeoutException.class, TimeoutException.class},
            traversingCauses = "true",
            autoCreateTopics = "false",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(
            topics = "inventory.released",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onInventoryReleased(ConsumerRecord<String, String> record) throws JsonProcessingException {
        InventoryReleasedEvent event =
                objectMapper.readValue(record.value(), InventoryReleasedEvent.class);
        sagaService.handleInventoryReleased(event, messageId(record));
    }

    // ---------------- PAYMENT ----------------

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delayExpression = "${kafka.retry.delay-ms:1000}",
                    multiplierExpression = "${kafka.retry.multiplier:2.0}",
                    maxDelayExpression = "${kafka.retry.max-delay-ms:10000}"),
            include = {TransientDataAccessException.class, DataAccessResourceFailureException.class,
                    RetriableException.class, SQLTransientException.class, ConnectException.class,
                    SocketException.class, SocketTimeoutException.class, TimeoutException.class},
            traversingCauses = "true",
            autoCreateTopics = "false",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(
            topics = "payment.success",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentSuccess(ConsumerRecord<String, String> record) throws JsonProcessingException {
        PaymentSuccessEvent event =
                objectMapper.readValue(record.value(), PaymentSuccessEvent.class);
        sagaService.handlePaymentSuccess(event, messageId(record));
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delayExpression = "${kafka.retry.delay-ms:1000}",
                    multiplierExpression = "${kafka.retry.multiplier:2.0}",
                    maxDelayExpression = "${kafka.retry.max-delay-ms:10000}"),
            include = {TransientDataAccessException.class, DataAccessResourceFailureException.class,
                    RetriableException.class, SQLTransientException.class, ConnectException.class,
                    SocketException.class, SocketTimeoutException.class, TimeoutException.class},
            traversingCauses = "true",
            autoCreateTopics = "false",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(
            topics = "payment.failed",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentFailed(ConsumerRecord<String, String> record) throws JsonProcessingException {
        PaymentFailedEvent event =
                objectMapper.readValue(record.value(), PaymentFailedEvent.class);
        sagaService.handlePaymentFailed(event, messageId(record));
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delayExpression = "${kafka.retry.delay-ms:1000}",
                    multiplierExpression = "${kafka.retry.multiplier:2.0}",
                    maxDelayExpression = "${kafka.retry.max-delay-ms:10000}"),
            include = {TransientDataAccessException.class, DataAccessResourceFailureException.class,
                    RetriableException.class, SQLTransientException.class, ConnectException.class,
                    SocketException.class, SocketTimeoutException.class, TimeoutException.class},
            traversingCauses = "true",
            autoCreateTopics = "false",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(
            topics = "payment.refunded",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentRefunded(ConsumerRecord<String, String> record) throws JsonProcessingException {
        PaymentRefundedEvent event =
                objectMapper.readValue(record.value(), PaymentRefundedEvent.class);
        sagaService.handlePaymentRefunded(event, messageId(record));
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delayExpression = "${kafka.retry.delay-ms:1000}",
                    multiplierExpression = "${kafka.retry.multiplier:2.0}",
                    maxDelayExpression = "${kafka.retry.max-delay-ms:10000}"),
            include = {TransientDataAccessException.class, DataAccessResourceFailureException.class,
                    RetriableException.class, SQLTransientException.class, ConnectException.class,
                    SocketException.class, SocketTimeoutException.class, TimeoutException.class},
            traversingCauses = "true",
            autoCreateTopics = "false",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(
            topics = "order.cancel.requested",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderCancelRequested(ConsumerRecord<String, String> record) throws JsonProcessingException {
        OrderCancelRequestedEvent event =
                objectMapper.readValue(record.value(), OrderCancelRequestedEvent.class);
        sagaService.handleCancelRequested(event, messageId(record));
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @Backoff(delayExpression = "${kafka.retry.delay-ms:1000}",
                    multiplierExpression = "${kafka.retry.multiplier:2.0}",
                    maxDelayExpression = "${kafka.retry.max-delay-ms:10000}"),
            include = {TransientDataAccessException.class, DataAccessResourceFailureException.class,
                    RetriableException.class, SQLTransientException.class, ConnectException.class,
                    SocketException.class, SocketTimeoutException.class, TimeoutException.class},
            traversingCauses = "true",
            autoCreateTopics = "false",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE)
    @KafkaListener(
            topics = {"order.confirmed", "order.cancelled"},
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderTerminalEvent(ConsumerRecord<String, String> record) throws JsonProcessingException {
        String messageId = messageId(record);
        if ("order.confirmed".equals(record.topic())) {
            OrderConfirmedEvent event = objectMapper.readValue(record.value(), OrderConfirmedEvent.class);
            sagaService.handleOrderConfirmed(event, messageId);
        } else {
            OrderCancelledEvent event = objectMapper.readValue(record.value(), OrderCancelledEvent.class);
            sagaService.handleOrderCancelled(event, messageId);
        }
    }

    private String messageId(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader("messageId");
        if (header == null || header.value() == null) {
            throw new IllegalArgumentException(
                    "Kafka message on " + record.topic() + " is missing messageId header");
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    @DltHandler
    public void onDeadLetter(ConsumerRecord<String, String> record) {
        JsonNode payload;
        try {
            payload = objectMapper.readTree(record.value());
        } catch (JsonProcessingException e) {
            log.warn("Could not extract identifiers from DLT payload topic={} partition={} offset={}",
                    record.topic(), record.partition(), record.offset(), e);
            payload = objectMapper.createObjectNode();
        }
        log.error("DLT topic={} sagaId={} orderId={} messageId={} failure={} payload={}",
                record.topic(), first(header(record, "sagaId"), payload.path("sagaId").asText(null)),
                first(header(record, "orderId"), payload.path("orderId").asText(null), record.key()),
                first(header(record, "messageId"), messageIdForDlt(record)),
                header(record, "kafka_dlt-exception-message"),
                record.value());
    }

    private String messageIdForDlt(ConsumerRecord<String, String> record) {
        return record.topic() + "-" + record.partition() + "-" + record.offset();
    }

    private String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "unknown";
    }

    private String header(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
