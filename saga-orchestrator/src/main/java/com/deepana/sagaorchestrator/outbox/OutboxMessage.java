package com.deepana.sagaorchestrator.outbox;

import java.util.UUID;

public record OutboxMessage(
        UUID messageId,
        String aggregateId,
        String topic,
        String payload,
        String headers) {
}
