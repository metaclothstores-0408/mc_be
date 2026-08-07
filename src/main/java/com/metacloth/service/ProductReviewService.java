package com.metacloth.service;

import com.metacloth.api.dto.ProductReviewDtos.*;
import com.metacloth.domain.entity.ProductReview;
import com.metacloth.repository.ProductRepository;
import com.metacloth.repository.ProductReviewRepository;
import com.metacloth.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductReviewService {

    private final ProductReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<ReviewResponse> getReviews(Long productId) {

        return reviewRepository
                .findByProductIdAndActiveTrueOrderByCreatedAtDesc(productId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductRatingResponse getRating(Long productId) {

        List<ProductReview> reviews =
                reviewRepository.findByProductIdAndActiveTrueOrderByCreatedAtDesc(productId);

        if (reviews.isEmpty()) {
            return new ProductRatingResponse(0.0, 0);
        }

        double average =
                reviews.stream()
                        .mapToInt(ProductReview::getRating)
                        .average()
                        .orElse(0.0);

        return new ProductRatingResponse(
                Math.round(average * 10.0) / 10.0,
                reviews.size()
        );
    }

    @Transactional
    public ReviewResponse createOrUpdate(
            Long productId,
            Long userId,
            CreateReviewRequest request
    ) {

        var product = productRepository.findById(productId)
                .orElseThrow(() ->
                        new EntityNotFoundException("Product not found"));

        var user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new EntityNotFoundException("User not found"));

        ProductReview review =
                reviewRepository
                        .findByProductIdAndUserId(productId, userId)
                        .orElseGet(ProductReview::new);

        review.setProduct(product);
        review.setUser(user);
        review.setRating(request.rating());
        review.setComment(request.comment());
        review.setActive(true);

        return toResponse(reviewRepository.save(review));
    }

    @Transactional
    public void delete(Long productId, Long userId) {

        ProductReview review =
                reviewRepository
                        .findByProductIdAndUserId(productId, userId)
                        .orElseThrow(() ->
                                new EntityNotFoundException("Review not found"));

        reviewRepository.delete(review);
    }

    @Transactional
    public void adminDeactivate(Long reviewId) {

        ProductReview review =
                reviewRepository.findById(reviewId)
                        .orElseThrow(() ->
                                new EntityNotFoundException("Review not found"));

        review.setActive(false);
    }

    private ReviewResponse toResponse(ProductReview review) {

        String name = review.getUser().getFullName();

        if (name == null || name.isBlank()) {
            name = "Customer";
        }

        return new ReviewResponse(
                review.getId(),
                review.getProduct().getId(),
                review.getUser().getId(),
                name,
                review.getRating(),
                review.getComment(),
                review.isActive(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }
}