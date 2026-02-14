package com.deepana.orderservice.controller;

import com.deepana.orderservice.dto.request.CreateOrderRequestDTO;
import com.deepana.orderservice.dto.response.OrderResponseDTO;
import com.deepana.orderservice.service.OrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // ================= CREATE =================

    @PostMapping
    public ResponseEntity<OrderResponseDTO> createOrder(
            @Valid @RequestBody CreateOrderRequestDTO request
    ) {

        // Generate traceId at entry point
        String traceId = "ORD-" + UUID.randomUUID()
                .toString()
                .substring(0, 8)
                .toUpperCase();

        MDC.put("traceId", traceId);

        try {

            OrderResponseDTO response =
                    orderService.createOrder(request);

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(response);

        } finally {
            MDC.clear();
        }
    }

    // ================= READ =================

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponseDTO> getOrderById(
            @PathVariable Long id
    ) {

        return ResponseEntity.ok(
                orderService.getOrderById(id)
        );
    }

    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<OrderResponseDTO> getByOrderNumber(
            @PathVariable String orderNumber
    ) {

        return ResponseEntity.ok(
                orderService.getByOrderNumber(orderNumber)
        );
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<OrderResponseDTO>> getOrdersByUser(
            @PathVariable Long userId) {

        return ResponseEntity.ok(
                orderService.getOrdersByUserId(userId)
        );
    }

    // ================= CANCEL =================

    @PutMapping("/{id}/cancel")
    public ResponseEntity<OrderResponseDTO> cancelOrder(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                orderService.cancelOrder(id)
        );
    }
}
