package com.deepana.inventoryservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class KafkaTopicsConfig {

    @Bean
    public KafkaAdmin.NewTopics inventoryKafkaTopics() {
        List<NewTopic> topics = new ArrayList<>();
        addMain(topics, "inventory.reserved");
        addMain(topics, "inventory.failed");
        addMain(topics, "inventory.released");
        addRetryTopics(topics, "inventory.reserve.cmd");
        addRetryTopics(topics, "inventory.release.cmd");
        addMain(topics, "inventory.reserve.cmd");
        addMain(topics, "inventory.release.cmd");
        return new KafkaAdmin.NewTopics(topics.toArray(NewTopic[]::new));
    }

    private void addRetryTopics(List<NewTopic> topics, String topic) {
        for (int attempt = 0; attempt < 3; attempt++) {
            addMain(topics, topic + "-retry-" + attempt);
        }
        addMain(topics, topic + "-dlt");
    }

    private void addMain(List<NewTopic> topics, String name) {
        topics.add(TopicBuilder.name(name).partitions(6).replicas(1).build());
    }
}
