package com.deepana.orderservice.controller;

import com.deepana.orderservice.dto.request.CreateOrderRequestDTO;
import com.deepana.orderservice.dto.response.OrderResponseDTO;
import com.deepana.orderservice.service.OrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * Create a new Order
     */
    @PostMapping
    public ResponseEntity<OrderResponseDTO> createOrder(
            @Valid @RequestBody CreateOrderRequestDTO request
    ) {

        OrderResponseDTO response = orderService.createOrder(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    /**
     * Get Order by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponseDTO> getOrderById(
            @PathVariable Long id
    ) {

        OrderResponseDTO response = orderService.getOrderById(id);

        return ResponseEntity.ok(response);
    }

    /**
     * Get Order by Order Number
     */
    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<OrderResponseDTO> getByOrderNumber(
            @PathVariable String orderNumber
    ) {

        OrderResponseDTO response =
                orderService.getByOrderNumber(orderNumber);

        return ResponseEntity.ok(response);
    }
}
