package com.deepana.inventoryservice;

import org.junit.jupiter.api.Test;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
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
class InventoryServiceApplicationTests extends IntegrationTestBase {

    @Autowired private Environment environment;
    @Autowired private ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory;

    @Test
    void contextLoadsAndCreatesInventoryTopicsWithMatchingConsumerGroup() throws Exception {
        assertThat(POSTGRES.isRunning()).isTrue();
        assertThat(KAFKA.isRunning()).isTrue();
        assertThat(environment.getProperty("spring.kafka.consumer.group-id")).isEqualTo("inventory-group");
        assertThat(kafkaListenerContainerFactory.getContainerProperties().getAckMode())
                .isEqualTo(ContainerProperties.AckMode.RECORD);
        for (Method method : com.deepana.inventoryservice.kafka.InventoryCommandConsumer.class.getDeclaredMethods()) {
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
                    "inventory.reserved", "inventory.failed", "inventory.released",
                    "inventory.reserve.cmd", "inventory.release.cmd",
                    "inventory.reserve.cmd-retry-0", "inventory.reserve.cmd-retry-1",
                    "inventory.reserve.cmd-retry-2", "inventory.reserve.cmd-dlt",
                    "inventory.release.cmd-retry-0", "inventory.release.cmd-retry-1",
                    "inventory.release.cmd-retry-2", "inventory.release.cmd-dlt");
            var inventoryTopics = names.stream().filter(name -> name.startsWith("inventory.")).toList();
            var descriptions = admin.describeTopics(inventoryTopics).allTopicNames().get(10, TimeUnit.SECONDS);
            assertThat(descriptions.values()).allSatisfy(topic ->
                    assertThat(topic.partitions()).hasSize(6));
        }
    }
}
