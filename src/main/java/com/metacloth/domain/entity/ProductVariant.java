package com.metacloth.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "product_variants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    Product product;
    @Column(name = "color_name")
    String colorName;
    @Column(name = "color_hex")
    String colorHex;
    String size;
    @Column(name = "stock_quantity")
    int stockQuantity;
    @Column(name = "mockup_front_url")
    String mockupFrontUrl;
    @Column(name = "mockup_back_url")
    String mockupBackUrl;
    @Column(name = "created_at")
    OffsetDateTime createdAt;
    @Column(name = "updated_at")
    OffsetDateTime updatedAt;
}
