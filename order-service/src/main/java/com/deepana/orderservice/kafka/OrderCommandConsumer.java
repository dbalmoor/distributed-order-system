package com.deepana.orderservice.kafka;

import com.deepana.orderservice.service.OrderService;
import com.deepana.saga.commondto.order.CancelOrderCommand;
import com.deepana.saga.commondto.order.ConfirmOrderCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.errors.RetriableException;
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
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLTransientException;
import java.util.concurrent.TimeoutException;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCommandConsumer {

    private final OrderService orderService;
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
    @KafkaListener(topics = "order.confirm.cmd", groupId = "${spring.kafka.consumer.group-id}")
    public void onConfirm(String message) throws JsonProcessingException {
        ConfirmOrderCommand command = objectMapper.readValue(message, ConfirmOrderCommand.class);
        orderService.confirmOrder(command);
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
    @KafkaListener(topics = "order.cancel.cmd", groupId = "${spring.kafka.consumer.group-id}")
    public void onCancel(String message) throws JsonProcessingException {
        CancelOrderCommand command = objectMapper.readValue(message, CancelOrderCommand.class);
        orderService.cancelBySaga(command);
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
