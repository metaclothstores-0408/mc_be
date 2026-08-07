package com.metacloth.api.controller;

import com.metacloth.api.dto.OrderDtos.OrderResponse;
import com.metacloth.api.dto.OrderDtos.StatusUpdateRequest;
import com.metacloth.repository.OrderRepository;
import com.metacloth.service.OrderService;
import com.metacloth.service.ProductReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {
    private final OrderService orders;
    private final OrderRepository repo;
    private final ProductReviewService reviewService;

    @GetMapping("/orders")
    List<OrderResponse> orders() {
        return orders.all();
    }

    @PutMapping("/orders/{id}/status")
    OrderResponse status(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest r) {
        return orders.status(id, r.status());
    }

    @GetMapping("/dashboard/stats")
    Map<String, Object> stats() {
        var all = repo.findAll();
        BigDecimal rev = all.stream().map(o -> o.getTotalAmount()).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        return Map.of("totalOrders", all.size(), "totalRevenue", rev);
    }

    @DeleteMapping("/reviews/{reviewId}")
    public void deleteReview(
            @PathVariable Long reviewId
    ) {
        reviewService.adminDeactivate(reviewId);
    }
}
