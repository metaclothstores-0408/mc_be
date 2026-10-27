package com.metacloth.api.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class AdminDtos {

    private AdminDtos() {
    }

    public record AdminUserResponse(
            Long id,
            String fullName,
            String email,
            String phoneNumber,
            String authProvider,
            boolean enabled,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }

    public record AdminStorageMetricsResponse(
            String bucketName,
            boolean isConfigured,
            long totalStorageBytes,
            double totalStorageMb,
            double freeTierCapacityMb,
            double freeTierUsedPercentage,
            double remainingFreeTierMb,
            int totalObjects,
            Map<String, Object> categoryBreakdown,
            List<UserStorageUsageDto> topUsers
    ) {
    }

    public record UserStorageUsageDto(
            Long userId,
            String email,
            long usedBytes,
            double usedMb,
            double percentOfQuota,
            long collectionsCount
    ) {
    }
}
