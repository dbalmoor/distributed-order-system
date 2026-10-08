package com.deepana.orderservice.service;

import com.deepana.orderservice.entity.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OrderStateMachine {

    public boolean transition(OrderStatus current, OrderStatus target, Long orderId) {
        if (current == target) {
            log.info("Ignoring repeated transition for order {} in terminal state {}", orderId, current);
            return false;
        }

        if (current == OrderStatus.CREATED
                && (target == OrderStatus.COMPLETED || target == OrderStatus.CANCELLED)) {
            return true;
        }

        log.warn("Rejecting illegal order status transition for order {}: {} -> {}",
                orderId, current, target);
        throw new IllegalStateException(
                "Cannot transition order " + orderId + " from " + current + " to " + target);
    }
}
