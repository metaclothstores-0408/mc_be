package com.metacloth.api.controller;

import com.metacloth.api.dto.OrderDtos.CreateOrderRequest;
import com.metacloth.api.dto.OrderDtos.OrderResponse;
import com.metacloth.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService service;

    @PostMapping
    OrderResponse create(Authentication a, @Valid @RequestBody CreateOrderRequest r) {
        return service.create((Long) a.getPrincipal(), r);
    }

    @GetMapping("/my-orders")
    List<OrderResponse> mine(Authentication a) {
        return service.mine((Long) a.getPrincipal());
    }

    @GetMapping("/{id}/track")
    Object track(Authentication a, @PathVariable Long id) {
        return service.track((Long) a.getPrincipal(), id);
    }
}
