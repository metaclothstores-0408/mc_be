package com.metacloth.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    Order order;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_variant_id")
    ProductVariant productVariant;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "avatar_id")
    Avatar avatar;
    int quantity;
    @Column(name = "unit_price")
    BigDecimal unitPrice;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "print_config_json", columnDefinition = "jsonb")
    String printConfigJson;
    @Column(name = "preview_render_url", columnDefinition = "text")
    String previewRenderUrl;
}

