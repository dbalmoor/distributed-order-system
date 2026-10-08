package com.deepana.orderservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class PhaseTwoTopicsConfiguration {

    @Bean
    NewTopic orderCancelRequestedTopic() {
        return TopicBuilder.name("order.cancel.requested").partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic orderConfirmedTopic() {
        return TopicBuilder.name("order.confirmed").partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic orderCancelledTopic() {
        return TopicBuilder.name("order.cancelled").partitions(1).replicas(1).build();
    }
}
