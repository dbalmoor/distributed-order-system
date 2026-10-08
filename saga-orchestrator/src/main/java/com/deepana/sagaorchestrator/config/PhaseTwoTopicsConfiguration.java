package com.deepana.sagaorchestrator.config;

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

    @Bean
    NewTopic inventoryReleaseCommandTopic() {
        return TopicBuilder.name("inventory.release.cmd").partitions(6).replicas(1).build();
    }

    @Bean
    NewTopic inventoryReleasedTopic() {
        return TopicBuilder.name("inventory.released").partitions(6).replicas(1).build();
    }

    @Bean
    NewTopic paymentRefundCommandTopic() {
        return TopicBuilder.name("payment.refund.cmd").partitions(6).replicas(1).build();
    }

    @Bean
    NewTopic paymentRefundedTopic() {
        return TopicBuilder.name("payment.refunded").partitions(6).replicas(1).build();
    }
}
