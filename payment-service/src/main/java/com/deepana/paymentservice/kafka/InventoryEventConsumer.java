package com.deepana.paymentservice.kafka;

import com.deepana.paymentservice.service.PaymentService;
import com.deepana.saga.commondto.payment.ChargePaymentCommand;
import com.deepana.saga.commondto.payment.RefundPaymentCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
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
    public void consume(String message) throws JsonProcessingException {

        try {

            ChargePaymentCommand cmd =
                    objectMapper.readValue(message, ChargePaymentCommand.class);

            MDC.put("traceId", cmd.getTraceId());

            log.info("Received payment.charge.cmd {}", message);

            paymentService.processPayment(cmd);

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
    public void consumeRefund(String message) throws JsonProcessingException {
        try {
            RefundPaymentCommand cmd = objectMapper.readValue(message, RefundPaymentCommand.class);
            MDC.put("traceId", cmd.getTraceId());
            log.info("Received payment.refund.cmd for order {}", cmd.getOrderId());
            paymentService.processRefund(cmd);
        } finally {
            MDC.clear();
        }
    }

    @DltHandler
    public void onDeadLetter(ConsumerRecord<String, String> record) {
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

    private String messageId(ConsumerRecord<String, String> record) {
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
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
