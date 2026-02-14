package com.deepana.saga.commondto.base;

import lombok.*;
import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BaseEvent {

    private String sagaId;
    private Long orderId;
    private String traceId;
    private Instant timestamp;
    private String orderNumber;

    public void init() {
        this.timestamp = Instant.now();
    }
}
