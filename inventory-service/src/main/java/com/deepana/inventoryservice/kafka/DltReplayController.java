package com.deepana.inventoryservice.kafka;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Unauthenticated operator endpoint, not exposed through the API gateway.
 */
@RestController
@RequestMapping("/admin/kafka/dlt")
@RequiredArgsConstructor
public class DltReplayController {

    private static final Set<String> SOURCE_TOPICS = Set.of(
            "inventory.reserve.cmd", "inventory.release.cmd");

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @PostMapping("/replay")
    public ReplayResult replay(@Valid @RequestBody ReplayRequest request) {
        String sourceTopic = request.dltTopic().endsWith("-dlt")
                ? request.dltTopic().substring(0, request.dltTopic().length() - 4) : "";
        if (!SOURCE_TOPICS.contains(sourceTopic)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported DLT topic");
        }
        ConsumerRecord<String, String> source = readDltRecord(request);
        if (!request.dryRun()) {
            ProducerRecord<String, String> replay = new ProducerRecord<>(
                    sourceTopic, source.partition(), source.key(), source.value());
            for (Header header : source.headers()) {
                replay.headers().add(new RecordHeader(header.key(), header.value()));
            }
            try {
                kafkaTemplate.send(replay).get(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while replaying DLT record", e);
            } catch (ExecutionException | TimeoutException e) {
                throw new IllegalStateException("Could not replay DLT record", e);
            }
        }
        return new ReplayResult(sourceTopic, source.partition(), source.offset(),
                source.key(), request.dryRun());
    }

    private ConsumerRecord<String, String> readDltRecord(ReplayRequest request) {
        Map<String, Object> properties = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "dlt-replay-" + UUID.randomUUID(),
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        TopicPartition partition = new TopicPartition(request.dltTopic(), request.partition());
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.assign(List.of(partition));
            consumer.seek(partition, request.offset());
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (System.nanoTime() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(250));
                for (ConsumerRecord<String, String> record : records) {
                    if (record.offset() == request.offset()) {
                        return record;
                    }
                }
            }
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid DLT partition or offset", e);
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DLT record was not found at that offset");
    }

    public record ReplayRequest(@NotBlank String dltTopic, @Min(0) int partition,
                                @Min(0) long offset, boolean dryRun) {
    }

    public record ReplayResult(String topic, int partition, long offset, String key, boolean dryRun) {
    }
}
