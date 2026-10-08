package com.deepana.paymentservice.config;

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
    public KafkaAdmin.NewTopics paymentKafkaTopics() {
        List<NewTopic> topics = new ArrayList<>();
        addMain(topics, "payment.success");
        addMain(topics, "payment.failed");
        addMain(topics, "payment.refunded");
        addRetryTopics(topics, "payment.charge.cmd");
        addRetryTopics(topics, "payment.refund.cmd");
        addMain(topics, "payment.charge.cmd");
        addMain(topics, "payment.refund.cmd");
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
