package com.metacloth.api.controller;

import com.metacloth.api.dto.OrderDtos.OrderResponse;
import com.metacloth.api.dto.OrderDtos.StatusUpdateRequest;
import com.metacloth.repository.OrderRepository;
import com.metacloth.service.OrderService;
import com.metacloth.service.ProductReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import com.metacloth.api.dto.AdminDtos.AdminUserResponse;
import com.metacloth.domain.entity.User;
import com.metacloth.domain.enums.UserRole;
import com.metacloth.exception.ApiException;
import com.metacloth.repository.UserRepository;
import org.springframework.http.HttpStatus;

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
    private final UserRepository users;

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

    @GetMapping("/users")
    public List<AdminUserResponse> users() {

        return users
                .findAllByRoleOrderByCreatedAtDesc(UserRole.CUSTOMER)
                .stream()
                .map(this::toAdminUser)
                .toList();
    }

    @DeleteMapping("/users/{id}")
    public void deleteUser(@PathVariable Long id) {

        User user = users.findById(id)
                .orElseThrow(() ->
                        new ApiException(
                                HttpStatus.NOT_FOUND,
                                "User not found."
                        )
                );

        if (user.getRole() == UserRole.ADMIN) {
            throw new ApiException(
                    HttpStatus.FORBIDDEN,
                    "Admin users cannot be deleted."
            );
        }

        if (repo.existsById(id)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "This user has orders and cannot be deleted. Delete or archive the orders first."
            );
        }

        users.delete(user);
    }

    private AdminUserResponse toAdminUser(User user) {

        return new AdminUserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getAuthProvider() == null
                        ? null
                        : user.getAuthProvider().name(),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
