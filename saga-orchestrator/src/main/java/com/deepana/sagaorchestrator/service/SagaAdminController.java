package com.deepana.sagaorchestrator.service;

import com.deepana.sagaorchestrator.repository.SagaAdminView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Direct orchestrator admin API. These endpoints are unauthenticated, matching the known
 * project limitation; they are not exposed through the API gateway.
 */
@RestController
@RequestMapping("/admin/sagas")
@RequiredArgsConstructor
public class SagaAdminController {

    private final SagaService sagaService;

    @GetMapping("/needs-attention")
    public List<SagaAdminView> listNeedingAttention() {
        return sagaService.listNeedingAttention();
    }

    @PostMapping("/{sagaId}/retry")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retry(@PathVariable String sagaId, @Valid @RequestBody RetryRequest request) {
        sagaService.retryNeedsAttention(sagaId, request.action().name());
    }

    @PostMapping("/{sagaId}/force-resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forceResolve(@PathVariable String sagaId, @Valid @RequestBody ForceResolveRequest request) {
        sagaService.forceResolve(sagaId, request.operatorNote());
    }

    public enum RetryAction {
        CONFIRM,
        COMPENSATION
    }

    public record RetryRequest(@NotNull RetryAction action) {
    }

    public record ForceResolveRequest(@NotBlank String operatorNote) {
    }
}
