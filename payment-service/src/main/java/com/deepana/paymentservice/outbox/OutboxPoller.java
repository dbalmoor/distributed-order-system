package com.deepana.paymentservice.outbox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@EnableScheduling
@Slf4j
public class OutboxPoller {

    private static final TypeReference<Map<String, String>> HEADERS = new TypeReference<>() { };
    private final OutboxTransactions transactions;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Tracer tracer;
    private final Propagator propagator;
    private final boolean enabled;
    private final int batchSize;
    private final long leaseMillis;

    public OutboxPoller(
            OutboxTransactions transactions,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            Tracer tracer,
            Propagator propagator,
            @Value("${outbox.poller.enabled:true}") boolean enabled,
            @Value("${outbox.poller.batch-size:50}") int batchSize,
            @Value("${outbox.poller.lease-ms:30000}") long leaseMillis) {
        this.transactions = transactions;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.tracer = tracer;
        this.propagator = propagator;
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.leaseMillis = leaseMillis;
    }

    @Scheduled(fixedDelayString = "${outbox.poller.interval-ms:500}")
    public void scheduledPoll() {
        if (enabled) {
            pollOnce();
        }
    }

    public void pollOnce() {
        transactions.resetExpiredLeases();
        for (OutboxMessage message : transactions.claim(batchSize, leaseMillis)) {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            Span span = null;
            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        message.topic(), message.aggregateId(), message.payload());
                Map<String, String> headers = objectMapper.readValue(message.headers(), HEADERS);
                setMdc(message, headers);
                headers.forEach((name, value) -> {
                    if (!isPropagationHeader(name)) {
                        record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
                    }
                });
                span = propagator.extract(headers, (carrier, key) -> carrier.get(key))
                        .name("outbox.publish").start();
                span.tag("messaging.destination.name", message.topic());
                span.tag("orderId", first(headers.get("orderId"), message.aggregateId()));
                span.tag("sagaId", first(headers.get("sagaId")));
                try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
                    kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
                }
            } catch (Exception e) {
                log.error("Could not publish outbox message {}", message.messageId(), e);
                transactions.markFailed(message.messageId(), e.toString());
                continue;
            } finally {
                if (span != null) {
                    span.end();
                }
                restoreMdc(previous);
            }
            transactions.markPublished(message.messageId());
        }
    }

    private void setMdc(OutboxMessage message, Map<String, String> headers) {
        MDC.put("sagaId", first(headers.get("sagaId")));
        MDC.put("orderId", first(headers.get("orderId"), message.aggregateId()));
        MDC.put("messageId", message.messageId().toString());
        MDC.put("eventType", first(headers.get("eventType"), message.eventType()));
        if (headers.containsKey("correlationId")) {
            MDC.put("correlationId", headers.get("correlationId"));
        }
    }

    private void restoreMdc(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }

    private String first(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "unknown";
    }

    private boolean isPropagationHeader(String name) {
        return "traceparent".equalsIgnoreCase(name)
                || "tracestate".equalsIgnoreCase(name)
                || "baggage".equalsIgnoreCase(name);
    }
}
