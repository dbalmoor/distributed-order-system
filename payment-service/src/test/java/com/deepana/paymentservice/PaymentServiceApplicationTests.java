package com.deepana.paymentservice;

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
class PaymentServiceApplicationTests extends IntegrationTestBase {

    @Autowired private Environment environment;
    @Autowired private ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory;

    @Test
    void contextLoadsAndCreatesPaymentTopicsWithMatchingConsumerGroup() throws Exception {
        assertThat(POSTGRES.isRunning()).isTrue();
        assertThat(KAFKA.isRunning()).isTrue();
        assertThat(environment.getProperty("spring.kafka.consumer.group-id")).isEqualTo("payment-group");
        assertThat(kafkaListenerContainerFactory.getContainerProperties().getAckMode())
                .isEqualTo(ContainerProperties.AckMode.RECORD);
        for (Method method : com.deepana.paymentservice.kafka.InventoryEventConsumer.class.getDeclaredMethods()) {
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
                    "payment.success", "payment.failed", "payment.refunded",
                    "payment.charge.cmd", "payment.refund.cmd",
                    "payment.charge.cmd-retry-0", "payment.charge.cmd-retry-1",
                    "payment.charge.cmd-retry-2", "payment.charge.cmd-dlt",
                    "payment.refund.cmd-retry-0", "payment.refund.cmd-retry-1",
                    "payment.refund.cmd-retry-2", "payment.refund.cmd-dlt");
            var paymentTopics = names.stream().filter(name -> name.startsWith("payment.")).toList();
            var descriptions = admin.describeTopics(paymentTopics).allTopicNames().get(10, TimeUnit.SECONDS);
            assertThat(descriptions.values()).allSatisfy(topic ->
                    assertThat(topic.partitions()).hasSize(6));
        }
    }
}
