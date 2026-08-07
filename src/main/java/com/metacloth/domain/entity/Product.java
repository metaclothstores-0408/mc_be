package com.metacloth.domain.entity;

import com.metacloth.domain.enums.GarmentType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    String name;
    @Column(columnDefinition = "TEXT")
    String description;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "garment_type", columnDefinition = "garment_type_enum")
    GarmentType garmentType;
    @Column(name = "base_price")
    BigDecimal basePrice;
    @Column(name = "print_surcharge")
    BigDecimal printSurcharge;
    @Column(name = "is_customizable")
    boolean customizable;
    @Column(name = "is_active")
    boolean active;
    @Column(name = "created_at")
    OffsetDateTime createdAt;
    @Column(name = "updated_at")
    OffsetDateTime updatedAt;
    @OneToMany(
            mappedBy = "product",
            fetch = FetchType.EAGER,
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    List<ProductVariant> variants = new ArrayList<>();
}
