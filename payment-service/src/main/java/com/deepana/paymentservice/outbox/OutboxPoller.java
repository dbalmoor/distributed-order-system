package com.deepana.paymentservice.outbox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
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
    private final boolean enabled;
    private final int batchSize;
    private final long leaseMillis;

    public OutboxPoller(
            OutboxTransactions transactions,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${outbox.poller.enabled:true}") boolean enabled,
            @Value("${outbox.poller.batch-size:50}") int batchSize,
            @Value("${outbox.poller.lease-ms:30000}") long leaseMillis) {
        this.transactions = transactions;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
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
            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        message.topic(), message.aggregateId(), message.payload());
                objectMapper.readValue(message.headers(), HEADERS).forEach((name, value) ->
                        record.headers().add(name, value.getBytes(StandardCharsets.UTF_8)));
                kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("Could not publish outbox message {}", message.messageId(), e);
                transactions.markFailed(message.messageId(), e.toString());
                continue;
            }
            transactions.markPublished(message.messageId());
        }
    }
}
