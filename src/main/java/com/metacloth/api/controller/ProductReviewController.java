package com.metacloth.api.controller;

import com.metacloth.api.dto.ProductReviewDtos.*;
import com.metacloth.security.CurrentUser;
import com.metacloth.service.ProductReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductReviewController {

    private final ProductReviewService reviewService;

    @GetMapping("/{productId}/reviews")
    public List<ReviewResponse> reviews(
            @PathVariable Long productId
    ) {
        return reviewService.getReviews(productId);
    }

    @GetMapping("/{productId}/rating")
    public ProductRatingResponse rating(
            @PathVariable Long productId
    ) {
        return reviewService.getRating(productId);
    }

    @PostMapping("/{productId}/reviews")
    public ReviewResponse createReview(
            @PathVariable Long productId,
            @Valid @RequestBody CreateReviewRequest request
    ) {

        Long userId = CurrentUser.id();

        return reviewService.createOrUpdate(
                productId,
                userId,
                request
        );
    }

    @DeleteMapping("/{productId}/reviews")
    public void deleteReview(
            @PathVariable Long productId
    ) {

        reviewService.delete(
                productId,
                CurrentUser.id()
        );
    }
}