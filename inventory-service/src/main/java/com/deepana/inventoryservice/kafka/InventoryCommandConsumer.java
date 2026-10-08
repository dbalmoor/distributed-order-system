package com.deepana.inventoryservice.kafka;

import com.deepana.inventoryservice.service.InventoryService;
import com.deepana.saga.commondto.inventory.ReleaseInventoryCommand;
import com.deepana.saga.commondto.inventory.ReserveInventoryCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.errors.RetriableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Service;
import org.slf4j.MDC;
import org.springframework.retry.annotation.Backoff;

import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLTransientException;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryCommandConsumer {

    private final ObjectMapper objectMapper;
    private final InventoryService inventoryService;

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
            topics = "inventory.reserve.cmd",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeReserveCommand(String message) {

        try {

            ReserveInventoryCommand cmd =
                    objectMapper.readValue(message, ReserveInventoryCommand.class);

            MDC.put("traceId", cmd.getTraceId());

            log.info("Received inventory.reserve.cmd: {}", cmd.getOrderId());

            inventoryService.processReserve(cmd);

        } catch (Exception e) {

            log.error("Inventory processing failed", e);
            throw new RuntimeException(e);

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
            topics = "inventory.release.cmd",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consumeReleaseCommand(String message) {

        try {

            ReleaseInventoryCommand cmd =
                    objectMapper.readValue(message, ReleaseInventoryCommand.class);

            MDC.put("traceId", cmd.getTraceId());

            log.info("Received inventory.release.cmd: {}", cmd.getOrderId());

            inventoryService.processRelease(cmd);

        } catch (Exception e) {

            log.error("Inventory release failed", e);
            throw new RuntimeException(e);

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
