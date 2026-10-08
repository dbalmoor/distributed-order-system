package com.deepana.orderservice.service;

import com.deepana.orderservice.entity.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStateMachineTest {

    private final OrderStateMachine stateMachine = new OrderStateMachine();

    @Test
    void permitsCreatedToCompleted() {
        assertThat(stateMachine.transition(OrderStatus.CREATED, OrderStatus.COMPLETED, 1L)).isTrue();
    }

    @Test
    void permitsCreatedToCancelled() {
        assertThat(stateMachine.transition(OrderStatus.CREATED, OrderStatus.CANCELLED, 1L)).isTrue();
    }

    @Test
    void repeatedCurrentStateIsNoOp() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(stateMachine.transition(status, status, 1L)).isFalse();
        }
    }

    @Test
    void rejectsCreatedToCreatedAsAnAppliedTransition() {
        assertThat(stateMachine.transition(OrderStatus.CREATED, OrderStatus.CREATED, 1L)).isFalse();
    }

    @Test
    void rejectsCompletedToCancelled() {
        assertThatThrownBy(() -> stateMachine.transition(
                OrderStatus.COMPLETED, OrderStatus.CANCELLED, 1L))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsCancelledToCompleted() {
        assertThatThrownBy(() -> stateMachine.transition(
                OrderStatus.CANCELLED, OrderStatus.COMPLETED, 1L))
                .isInstanceOf(IllegalStateException.class);
    }
}
