package com.deepana.paymentservice.outbox;

import java.util.UUID;

public record OutboxMessage(
        UUID messageId,
        String aggregateType,
        String aggregateId,
        String eventType,
        String topic,
        String payload,
        String headers) {
}
