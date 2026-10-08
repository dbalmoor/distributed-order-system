package com.deepana.sagaorchestrator.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class KafkaTopicsConfig {

    private static final List<String> CONSUMED_TOPICS = List.of(
            "order.created",
            "order.cancel.requested",
            "order.confirmed",
            "order.cancelled",
            "inventory.reserved",
            "inventory.failed",
            "inventory.released",
            "payment.success",
            "payment.failed",
            "payment.refunded");

    @Bean
    public KafkaAdmin.NewTopics sagaKafkaTopics() {
        List<NewTopic> topics = new ArrayList<>();
        for (String topic : CONSUMED_TOPICS) {
            addMain(topics, topic);
            for (int attempt = 0; attempt < 3; attempt++) {
                addMain(topics, topic + "-retry-" + attempt);
            }
            addMain(topics, topic + "-dlt");
        }
        addMain(topics, "order.confirm.cmd");
        addMain(topics, "order.cancel.cmd");
        addMain(topics, "inventory.reserve.cmd");
        addMain(topics, "inventory.release.cmd");
        addMain(topics, "payment.charge.cmd");
        addMain(topics, "payment.refund.cmd");
        return new KafkaAdmin.NewTopics(topics.toArray(NewTopic[]::new));
    }

    private void addMain(List<NewTopic> topics, String name) {
        topics.add(TopicBuilder.name(name).partitions(6).replicas(1).build());
    }
}
