package com.metacloth.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public class ProductReviewDtos {

    public record CreateReviewRequest(

            @NotNull
            @Min(1)
            @Max(5)
            Integer rating,

            @Size(max = 1000)
            String comment
    ) {}

    public record ReviewResponse(
            Long id,
            Long productId,
            Long userId,
            String userName,
            Integer rating,
            String comment,
            boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}

    public record ProductRatingResponse(
            double averageRating,
            long reviewCount
    ) {}
}