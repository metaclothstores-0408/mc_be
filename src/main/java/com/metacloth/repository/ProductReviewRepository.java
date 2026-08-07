package com.metacloth.repository;

import com.metacloth.domain.entity.ProductReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductReviewRepository
        extends JpaRepository<ProductReview, Long> {

    List<ProductReview> findByProductIdAndActiveTrueOrderByCreatedAtDesc(
            Long productId
    );

    Optional<ProductReview> findByProductIdAndUserId(
            Long productId,
            Long userId
    );

    List<ProductReview> findByProductIdOrderByCreatedAtDesc(
            Long productId
    );
}