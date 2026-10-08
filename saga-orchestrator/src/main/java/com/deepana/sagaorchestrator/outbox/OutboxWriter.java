package com.deepana.sagaorchestrator.outbox;

import com.deepana.saga.commondto.base.BaseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID write(String topic, BaseEvent event) {
        if (event.getOrderId() == null) {
            throw new IllegalArgumentException("Saga outbox messages require an orderId");
        }
        UUID messageId = UUID.randomUUID();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("messageId", messageId.toString());
        putIfPresent(headers, "sagaId", event.getSagaId());
        headers.put("orderId", event.getOrderId().toString());
        putIfPresent(headers, "traceId", MDC.get("traceId") != null ? MDC.get("traceId") : event.getTraceId());
        headers.put("eventType", topic);
        try {
            jdbcTemplate.update("""
                    INSERT INTO outbox
                        (message_id, aggregate_type, aggregate_id, event_type, topic, payload, headers, created_at)
                    VALUES (?, 'ORDER', ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), clock_timestamp())
                    """,
                    messageId,
                    event.getOrderId().toString(),
                    topic,
                    topic,
                    objectMapper.writeValueAsString(event),
                    objectMapper.writeValueAsString(headers));
            return messageId;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize saga command " + topic, e);
        }
    }

    private void putIfPresent(Map<String, String> headers, String name, String value) {
        if (value != null && !value.isBlank()) {
            headers.put(name, value);
        }
    }
}
