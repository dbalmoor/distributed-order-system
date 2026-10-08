package com.deepana.sagaorchestrator.service;

import com.deepana.sagaorchestrator.entity.SagaSnapshot;
import com.deepana.sagaorchestrator.entity.SagaStatus;
import com.deepana.sagaorchestrator.entity.SagaStep;
import com.deepana.sagaorchestrator.entity.SagaTransition;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SagaStateMachine {

    public Optional<SagaTransition> transition(SagaSnapshot current, SagaTrigger trigger) {
        if (current == null) {
            return Optional.empty();
        }

        return switch (trigger) {
            case INVENTORY_RESERVED -> is(current, SagaStatus.ACTIVE, SagaStep.RESERVE_INVENTORY)
                    ? next(SagaStatus.ACTIVE, SagaStep.CHARGE_PAYMENT) : Optional.empty();
            case INVENTORY_FAILED -> is(current, SagaStatus.ACTIVE, SagaStep.RESERVE_INVENTORY)
                    ? next(SagaStatus.COMPENSATING, SagaStep.CANCEL_ORDER) : Optional.empty();
            case PAYMENT_SUCCESS -> is(current, SagaStatus.ACTIVE, SagaStep.CHARGE_PAYMENT)
                    ? next(SagaStatus.ACTIVE, SagaStep.CONFIRM_ORDER) : Optional.empty();
            case PAYMENT_FAILED -> is(current, SagaStatus.ACTIVE, SagaStep.CHARGE_PAYMENT)
                    ? next(SagaStatus.COMPENSATING, SagaStep.CANCEL_ORDER) : Optional.empty();
            case ORDER_CONFIRMED -> is(current, SagaStatus.ACTIVE, SagaStep.CONFIRM_ORDER)
                    ? next(SagaStatus.COMPLETED, SagaStep.COMPLETED) : Optional.empty();
            case ORDER_CANCELLED -> is(current, SagaStatus.COMPENSATING, SagaStep.CANCEL_ORDER)
                    ? next(SagaStatus.CANCELLED, SagaStep.CANCELLED) : Optional.empty();
            case CANCEL_REQUESTED -> is(current, SagaStatus.ACTIVE, SagaStep.RESERVE_INVENTORY)
                    ? next(SagaStatus.COMPENSATING, SagaStep.CANCEL_ORDER) : Optional.empty();
        };
    }

    private boolean is(SagaSnapshot current, SagaStatus status, SagaStep step) {
        return current.status() == status && current.currentStep() == step;
    }

    private Optional<SagaTransition> next(SagaStatus status, SagaStep step) {
        return Optional.of(new SagaTransition(status, step));
    }
}
