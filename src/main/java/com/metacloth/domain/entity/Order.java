package com.metacloth.domain.entity;

import com.metacloth.domain.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(name = "order_number")
    String orderNumber;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    User user;
    @Column(name = "total_amount")
    BigDecimal totalAmount;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "order_status", columnDefinition = "order_status_enum")
    OrderStatus orderStatus;
    @Column(name = "shipping_name")
    String shippingName;
    @Column(name = "shipping_phone")
    String shippingPhone;
    @Column(name = "shipping_address_line")
    String shippingAddressLine;
    String city;
    @Column(name = "postal_code")
    String postalCode;
    @Column(name = "created_at")
    OffsetDateTime createdAt;
    @Column(name = "updated_at")
    OffsetDateTime updatedAt;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    List<OrderItem> items = new ArrayList<>();

    @PrePersist
    void p() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
        if (orderStatus == null) orderStatus = OrderStatus.PLACED;
    }

    @PreUpdate
    void u() {
        updatedAt = OffsetDateTime.now();
    }
}
