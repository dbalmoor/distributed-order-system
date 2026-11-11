package com.deepana.orderservice.common.logging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.time.LocalDateTime;

@Slf4j
public class SagaLogger {

    private static final String FORMAT =
            "[SAGA] [SERVICE:%s] [CORRELATION:%s] [ORDER:%s] [STEP:%s] [STATUS:%s] at %s";

    public static void log(
            String service,
            String orderNumber,
            String step,
            String status
    ) {

        String correlationId = MDC.get("correlationId");
        if (orderNumber == null) {
            orderNumber = correlationId; // fallback
        }
        String message = String.format(
                FORMAT,
                service,
                correlationId,
                orderNumber,
                step,
                status,
                LocalDateTime.now()
        );

        log.info(message);
    }

    public static void success(
            String service,
            String orderNumber,
            String step
    ) {
        log(service, orderNumber, step, "SUCCESS");
    }

    public static void failed(
            String service,
            String orderNumber,
            String step
    ) {
        log(service, orderNumber, step, "FAILED");
    }
}
