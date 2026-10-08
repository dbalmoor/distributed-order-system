package com.deepana.sagaorchestrator.service;

import com.deepana.sagaorchestrator.entity.SagaSnapshot;
import com.deepana.sagaorchestrator.entity.SagaStatus;
import com.deepana.sagaorchestrator.entity.SagaStep;
import com.deepana.sagaorchestrator.entity.SagaTransition;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SagaStateMachineTest {

    private final SagaStateMachine stateMachine = new SagaStateMachine();

    @Test
    void allowsEverySagaTransitionInThePhaseFiveATable() {
        Map<Case, SagaTransition> transitions = Map.ofEntries(
                Map.entry(new Case(SagaStatus.ACTIVE, SagaStep.RESERVE_INVENTORY, SagaTrigger.INVENTORY_RESERVED),
                        new SagaTransition(SagaStatus.ACTIVE, SagaStep.CHARGE_PAYMENT)),
                Map.entry(new Case(SagaStatus.ACTIVE, SagaStep.RESERVE_INVENTORY, SagaTrigger.INVENTORY_FAILED),
                        new SagaTransition(SagaStatus.COMPENSATING, SagaStep.CANCEL_ORDER)),
                Map.entry(new Case(SagaStatus.ACTIVE, SagaStep.CHARGE_PAYMENT, SagaTrigger.PAYMENT_SUCCESS),
                        new SagaTransition(SagaStatus.ACTIVE, SagaStep.CONFIRM_ORDER)),
                Map.entry(new Case(SagaStatus.ACTIVE, SagaStep.CHARGE_PAYMENT, SagaTrigger.PAYMENT_FAILED),
                        new SagaTransition(SagaStatus.COMPENSATING, SagaStep.CANCEL_ORDER)),
                Map.entry(new Case(SagaStatus.COMPENSATING, SagaStep.CANCEL_ORDER, SagaTrigger.INVENTORY_RELEASED),
                        new SagaTransition(SagaStatus.COMPENSATING, SagaStep.CANCEL_ORDER)),
                Map.entry(new Case(SagaStatus.COMPENSATING, SagaStep.CANCEL_ORDER, SagaTrigger.PAYMENT_REFUNDED),
                        new SagaTransition(SagaStatus.COMPENSATING, SagaStep.CANCEL_ORDER)),
                Map.entry(new Case(SagaStatus.ACTIVE, SagaStep.CONFIRM_ORDER, SagaTrigger.ORDER_CONFIRMED),
                        new SagaTransition(SagaStatus.COMPLETED, SagaStep.COMPLETED)),
                Map.entry(new Case(SagaStatus.COMPENSATING, SagaStep.CANCEL_ORDER, SagaTrigger.ORDER_CANCELLED),
                        new SagaTransition(SagaStatus.CANCELLED, SagaStep.CANCELLED)),
                Map.entry(new Case(SagaStatus.ACTIVE, SagaStep.RESERVE_INVENTORY, SagaTrigger.CANCEL_REQUESTED),
                        new SagaTransition(SagaStatus.COMPENSATING, SagaStep.CANCEL_ORDER)),
                Map.entry(new Case(SagaStatus.ACTIVE, SagaStep.CHARGE_PAYMENT, SagaTrigger.CANCEL_REQUESTED),
                        new SagaTransition(SagaStatus.COMPENSATING, SagaStep.CANCEL_ORDER)));

        transitions.forEach((input, expected) ->
                assertThat(stateMachine.transition(snapshot(input.status(), input.step()), input.trigger()))
                        .contains(expected));
    }

    @Test
    void rejectsEveryTriggerWhenItDoesNotMatchTheCurrentStep() {
        for (SagaStatus status : SagaStatus.values()) {
            for (SagaStep step : SagaStep.values()) {
                for (SagaTrigger trigger : SagaTrigger.values()) {
                    boolean isAllowed = (status == SagaStatus.ACTIVE && step == SagaStep.RESERVE_INVENTORY
                            && (trigger == SagaTrigger.INVENTORY_RESERVED || trigger == SagaTrigger.INVENTORY_FAILED
                            || trigger == SagaTrigger.CANCEL_REQUESTED))
                            || (status == SagaStatus.ACTIVE && step == SagaStep.CHARGE_PAYMENT
                            && (trigger == SagaTrigger.PAYMENT_SUCCESS || trigger == SagaTrigger.PAYMENT_FAILED
                            || trigger == SagaTrigger.CANCEL_REQUESTED))
                            || (status == SagaStatus.ACTIVE && step == SagaStep.CONFIRM_ORDER
                            && trigger == SagaTrigger.ORDER_CONFIRMED)
                            || (status == SagaStatus.COMPENSATING && step == SagaStep.CANCEL_ORDER
                            && (trigger == SagaTrigger.ORDER_CANCELLED || trigger == SagaTrigger.INVENTORY_RELEASED
                            || trigger == SagaTrigger.PAYMENT_REFUNDED))
                            || (status == SagaStatus.CANCELLED && step == SagaStep.CANCELLED
                            && (trigger == SagaTrigger.INVENTORY_RELEASED || trigger == SagaTrigger.PAYMENT_REFUNDED));
                    if (!isAllowed) {
                        assertThat(stateMachine.transition(snapshot(status, step), trigger)).isEmpty();
                    }
                }
            }
        }
    }

    private SagaSnapshot snapshot(SagaStatus status, SagaStep step) {
        return new SagaSnapshot(UUIDs.SAGA_ID, 1L, status, step, 0L);
    }

    private record Case(SagaStatus status, SagaStep step, SagaTrigger trigger) { }

    private static final class UUIDs {
        private static final String SAGA_ID = "9f96e9ec-33be-4918-9973-c73fa2f036a7";
    }
}
