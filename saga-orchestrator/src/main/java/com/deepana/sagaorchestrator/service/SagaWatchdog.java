package com.deepana.sagaorchestrator.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "saga.watchdog", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SagaWatchdog {

    private final SagaServiceImpl sagaService;

    @Scheduled(fixedDelayString = "${saga.watchdog.interval-ms:1000}")
    public void runOnce() {
        sagaService.handleExpiredSagas();
    }
}
