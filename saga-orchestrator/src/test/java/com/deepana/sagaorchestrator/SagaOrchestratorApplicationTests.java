package com.deepana.sagaorchestrator;

import org.apache.kafka.clients.admin.AdminClient;
import org.junit.jupiter.api.Test;
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
class SagaOrchestratorApplicationTests extends IntegrationTestBase {

    @Autowired private Environment environment;
    @Autowired private ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory;

    @Test
    void contextLoadsAndCreatesSagaTopicsWithMatchingConsumerGroup() throws Exception {
        assertThat(environment.getProperty("spring.kafka.consumer.group-id")).isEqualTo("saga-group");
        assertThat(kafkaListenerContainerFactory.getContainerProperties().getAckMode())
                .isEqualTo(ContainerProperties.AckMode.RECORD);
        for (Method method : com.deepana.sagaorchestrator.kafka.SagaEventConsumer.class.getDeclaredMethods()) {
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
                    "inventory.reserved", "inventory.failed", "inventory.released",
                    "payment.success", "payment.failed", "payment.refunded",
                    "order.confirm.cmd", "order.cancel.cmd",
                    "inventory.reserve.cmd", "inventory.release.cmd",
                    "payment.charge.cmd", "payment.refund.cmd",
                    "order.created-retry-0", "order.created-retry-1", "order.created-retry-2",
                    "order.created-dlt", "payment.success-retry-0", "payment.success-dlt");
            var businessTopics = names.stream()
                    .filter(name -> name.startsWith("order.") || name.startsWith("inventory.")
                            || name.startsWith("payment."))
                    .toList();
            var descriptions = admin.describeTopics(businessTopics).allTopicNames().get(10, TimeUnit.SECONDS);
            assertThat(descriptions.values()).allSatisfy(topic ->
                    assertThat(topic.partitions()).hasSize(6));
        }
    }

}
