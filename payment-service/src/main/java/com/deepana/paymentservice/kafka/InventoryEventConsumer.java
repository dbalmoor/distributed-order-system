package com.deepana.paymentservice.kafka;

import com.deepana.paymentservice.service.PaymentService;
import com.deepana.saga.commondto.payment.ChargePaymentCommand;
import com.deepana.saga.commondto.payment.RefundPaymentCommand;
import com.deepana.saga.commondto.base.BaseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.errors.RetriableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.annotation.DltHandler;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.stereotype.Component;
import org.springframework.retry.annotation.Backoff;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLTransientException;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryEventConsumer {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

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
            topics = "payment.charge.cmd",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(ConsumerRecord<String, String> record) throws JsonProcessingException {
        try {
            ChargePaymentCommand cmd =
                    objectMapper.readValue(record.value(), ChargePaymentCommand.class);
            withMdc(record, cmd, "payment.charge.cmd", () -> {
                log.info("Received payment.charge.cmd {}", record.value());
                paymentService.processPayment(cmd);
            });
        } finally {
            MDC.clear();
        }
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
            topics = "payment.refund.cmd",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeRefund(ConsumerRecord<String, String> record) throws JsonProcessingException {
        try {
            RefundPaymentCommand cmd = objectMapper.readValue(record.value(), RefundPaymentCommand.class);
            withMdc(record, cmd, "payment.refund.cmd", () -> {
                log.info("Received payment.refund.cmd for order {}", cmd.getOrderId());
                paymentService.processRefund(cmd);
            });
        } finally {
            MDC.clear();
        }
    }

    @DltHandler
    public void onDeadLetter(ConsumerRecord<String, String> record) {
        meterRegistry.counter("kafka.dlt.messages", "topic", record.topic()).increment();
        JsonNode payload = dltPayload(record);
        log.error("DLT topic={} sagaId={} orderId={} messageId={} failure={} payload={}",
                record.topic(), first(header(record, "sagaId"), payload.path("sagaId").asText(null)),
                first(header(record, "orderId"), payload.path("orderId").asText(null), record.key()),
                first(header(record, "messageId"), messageId(record)),
                header(record, "kafka_dlt-exception-message"),
                record.value());
    }

    private JsonNode dltPayload(ConsumerRecord<String, String> record) {
        try {
            return objectMapper.readTree(record.value());
        } catch (JsonProcessingException e) {
            log.warn("Could not extract identifiers from DLT payload topic={} partition={} offset={}",
                    record.topic(), record.partition(), record.offset(), e);
            return objectMapper.createObjectNode();
        }
    }

    private String header(ConsumerRecord<String, String> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private void withMdc(ConsumerRecord<String, String> record, BaseEvent event,
                         String eventType, Runnable action) {
        putMdc("sagaId", first(header(record, "sagaId"), event.getSagaId()));
        putMdc("orderId", first(header(record, "orderId"),
                event.getOrderId() == null ? null : event.getOrderId().toString(), record.key()));
        putMdc("messageId", first(header(record, "messageId"), messageId(record)));
        putMdc("eventType", first(header(record, "eventType"), eventType));
        putMdc("traceId", event.getTraceId());
        try {
            action.run();
        } finally {
            MDC.clear();
        }
    }

    private void putMdc(String key, String value) {
        if (value != null && !value.isBlank()) {
            MDC.put(key, value);
        }
    }

    private String messageId(ConsumerRecord<String, String> record) {
        return first(header(record, "messageId"),
                record.topic() + "-" + record.partition() + "-" + record.offset());
    }

    private String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "unknown";
    }
}
