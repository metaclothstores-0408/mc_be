--liquibase formatted sql

--changeset metacloth:003

CREATE TABLE product_reviews (
    id BIGSERIAL PRIMARY KEY,

    product_id BIGINT NOT NULL
        REFERENCES products(id)
        ON DELETE CASCADE,

    user_id BIGINT NOT NULL
        REFERENCES users(id)
        ON DELETE CASCADE,

    rating INT NOT NULL,

    comment TEXT,

    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_product_review_rating
        CHECK (rating BETWEEN 1 AND 5),

    CONSTRAINT uk_product_review_user
        UNIQUE(product_id, user_id)
);

CREATE INDEX idx_product_reviews_product
    ON product_reviews(product_id);

CREATE INDEX idx_product_reviews_user
    ON product_reviews(user_id);

CREATE INDEX idx_product_reviews_active
    ON product_reviews(is_active);