package com.metacloth.api.controller;

import com.metacloth.api.dto.AdminDtos.*;
import com.metacloth.api.dto.OrderDtos.OrderResponse;
import com.metacloth.api.dto.OrderDtos.StatusUpdateRequest;
import com.metacloth.domain.entity.User;
import com.metacloth.domain.enums.UserRole;
import com.metacloth.exception.ApiException;
import com.metacloth.repository.DtfDesignCollectionRepository;
import com.metacloth.repository.OrderRepository;
import com.metacloth.repository.UserRepository;
import com.metacloth.service.OrderService;
import com.metacloth.service.ProductReviewService;
import com.metacloth.service.R2StorageService;
import com.metacloth.service.StorageCleanupScheduler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {
    private final OrderService orders;
    private final OrderRepository repo;
    private final ProductReviewService reviewService;
    private final UserRepository users;
    private final R2StorageService r2Storage;
    private final DtfDesignCollectionRepository collections;
    private final StorageCleanupScheduler cleanupScheduler;

    @GetMapping("/orders")
    public List<OrderResponse> orders() {
        return orders.all();
    }

    @PutMapping("/orders/{id}/status")
    public OrderResponse status(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest r) {
        return orders.status(id, r.status());
    }

    @GetMapping("/dashboard/stats")
    public Map<String, Object> stats() {
        var all = repo.findAll();
        BigDecimal rev = all.stream().map(o -> o.getTotalAmount()).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        return Map.of("totalOrders", all.size(), "totalRevenue", rev);
    }

    @DeleteMapping("/reviews/{reviewId}")
    public void deleteReview(@PathVariable Long reviewId) {
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
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));

        if (user.getRole() == UserRole.ADMIN) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Admin users cannot be deleted.");
        }

        if (repo.existsByUserId(id)) {
            throw new ApiException(HttpStatus.CONFLICT, "This user has orders and cannot be deleted. Delete or archive the orders first.");
        }

        users.delete(user);
    }

    /**
     * Admin Cloudflare R2 Storage Metrics & Visibility API.
     * Reports total bucket bytes, free tier (10 GB) consumption, category breakdown, and top user storage.
     */
    @GetMapping("/storage/metrics")
    public AdminStorageMetricsResponse getStorageMetrics() {
        R2StorageService.BucketMetrics metrics = r2Storage.getBucketMetrics();
        long totalBytes = metrics.totalBytes();
        double totalMb = Math.round((totalBytes / (1024.0 * 1024.0)) * 100.0) / 100.0;
        double freeTierCapacityMb = 10240.0; // 10 GB
        double usedPercent = Math.round((totalMb / freeTierCapacityMb) * 10000.0) / 100.0;
        double remainingMb = Math.max(0.0, Math.round((freeTierCapacityMb - totalMb) * 100.0) / 100.0);

        Map<String, Object> categories = new LinkedHashMap<>();
        categories.put("collectionsMb", Math.round((metrics.collectionsBytes() / (1024.0 * 1024.0)) * 100.0) / 100.0);
        categories.put("productionMb", Math.round((metrics.productionBytes() / (1024.0 * 1024.0)) * 100.0) / 100.0);
        categories.put("curatedMb", Math.round((metrics.curatedBytes() / (1024.0 * 1024.0)) * 100.0) / 100.0);
        categories.put("otherMb", Math.round((metrics.otherBytes() / (1024.0 * 1024.0)) * 100.0) / 100.0);
        categories.put("objectCounts", metrics.categoryCount());

        List<UserStorageUsageDto> topUsers = new ArrayList<>();
        List<Object[]> userBreakdown = collections.getUserStorageBreakdown();
        for (Object[] row : userBreakdown) {
            Long uid = (Long) row[0];
            String email = (String) row[1];
            Long bytes = (Long) row[2];
            Long count = (Long) row[3];
            long uBytes = bytes != null ? bytes : 0L;
            double uMb = Math.round((uBytes / (1024.0 * 1024.0)) * 100.0) / 100.0;
            double quotaPct = Math.round(((double) uBytes / R2StorageService.USER_STORAGE_QUOTA_BYTES) * 10000.0) / 100.0;
            topUsers.add(new UserStorageUsageDto(uid, email, uBytes, uMb, quotaPct, count != null ? count : 0L));
        }

        return new AdminStorageMetricsResponse(
                r2Storage.getBucketName(),
                r2Storage.isConfigured(),
                totalBytes,
                totalMb,
                freeTierCapacityMb,
                usedPercent,
                remainingMb,
                metrics.totalObjects(),
                categories,
                topUsers
        );
    }

    /**
     * Admin manual trigger for storage lifecycle purge.
     */
    @PostMapping("/storage/cleanup")
    public StorageCleanupScheduler.CleanupReport runStorageCleanup() {
        return cleanupScheduler.runScheduledCleanup();
    }

    private AdminUserResponse toAdminUser(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getAuthProvider() == null ? null : user.getAuthProvider().name(),
                user.isEnabled(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
