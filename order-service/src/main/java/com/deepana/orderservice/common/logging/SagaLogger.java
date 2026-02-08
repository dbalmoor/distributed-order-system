package com.deepana.orderservice.common.logging;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.time.LocalDateTime;

@Slf4j
public class SagaLogger {

    private static final String FORMAT =
            "[SAGA] [SERVICE:%s] [TRACE:%s] [ORDER:%s] [STEP:%s] [STATUS:%s] at %s";

    public static void log(
            String service,
            String orderNumber,
            String step,
            String status
    ) {

        String traceId = MDC.get("traceId");
        if (orderNumber == null) {
            orderNumber = traceId; // fallback
        }
        String message = String.format(
                FORMAT,
                service,
                traceId,
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

