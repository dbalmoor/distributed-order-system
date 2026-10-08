package com.deepana.orderservice;

import org.junit.jupiter.api.Test;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderServiceApplicationTests extends IntegrationTestBase {

    @Autowired private Environment environment;
    @Autowired private ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory;

    @Test
    void contextLoadsAndCreatesOrderTopicsWithMatchingConsumerGroup() throws Exception {
        assertThat(POSTGRES.isRunning()).isTrue();
        assertThat(KAFKA.isRunning()).isTrue();
        assertThat(environment.getProperty("spring.kafka.consumer.group-id")).isEqualTo("order-group");
        assertThat(kafkaListenerContainerFactory.getContainerProperties().getAckMode())
                .isEqualTo(ContainerProperties.AckMode.RECORD);
        for (Method method : com.deepana.orderservice.kafka.OrderCommandConsumer.class.getDeclaredMethods()) {
            KafkaListener listener = method.getAnnotation(KafkaListener.class);
            if (listener != null) {
                assertThat(listener.groupId()).isEqualTo("${spring.kafka.consumer.group-id}");
                assertThat(method.getAnnotation(RetryableTopic.class)).isNotNull();
                assertThat(method.getAnnotation(RetryableTopic.class).attempts()).isEqualTo("4");
            }
        }
        try (AdminClient admin = AdminClient.create(
                Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            Set<String> names = admin.listTopics().names().get(10, TimeUnit.SECONDS);
            assertThat(names).contains(
                    "order.created", "order.cancel.requested", "order.confirmed", "order.cancelled",
                    "order.confirm.cmd", "order.cancel.cmd",
                    "order.confirm.cmd-retry-0", "order.confirm.cmd-retry-1",
                    "order.confirm.cmd-retry-2", "order.confirm.cmd-dlt",
                    "order.cancel.cmd-retry-0", "order.cancel.cmd-retry-1",
                    "order.cancel.cmd-retry-2", "order.cancel.cmd-dlt");
            var orderTopics = names.stream().filter(name -> name.startsWith("order.")).toList();
            var descriptions = admin.describeTopics(orderTopics).allTopicNames().get(10, TimeUnit.SECONDS);
            assertThat(descriptions.values()).allSatisfy(topic ->
                    assertThat(topic.partitions()).hasSize(6));
        }
    }
}
