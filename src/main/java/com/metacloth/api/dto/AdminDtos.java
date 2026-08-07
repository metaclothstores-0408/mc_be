package com.metacloth.api.dto;

import java.time.OffsetDateTime;

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
}