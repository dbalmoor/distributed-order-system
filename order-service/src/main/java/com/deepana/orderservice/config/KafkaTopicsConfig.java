package com.deepana.orderservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.apache.kafka.clients.admin.NewTopic;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class KafkaTopicsConfig {

    @Bean
    public KafkaAdmin.NewTopics orderKafkaTopics() {
        List<NewTopic> topics = new ArrayList<>();
        addMain(topics, "order.created");
        addMain(topics, "order.cancel.requested");
        addMain(topics, "order.confirmed");
        addMain(topics, "order.cancelled");
        addRetryTopics(topics, "order.confirm.cmd");
        addRetryTopics(topics, "order.cancel.cmd");
        addMain(topics, "order.confirm.cmd");
        addMain(topics, "order.cancel.cmd");
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
