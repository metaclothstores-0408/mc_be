package com.metacloth.domain.entity;

import com.metacloth.domain.enums.AuthProvider;
import com.metacloth.domain.enums.UserRole;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(name = "full_name")
    String fullName;
    @Column(unique = true)
    String email;
    @Column(name = "phone_number", unique = true)
    String phoneNumber;
    @Column(name = "password_hash")
    String passwordHash;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "auth_provider", columnDefinition = "auth_provider_enum")
    AuthProvider authProvider;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "user_role_enum")
    UserRole role;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_avatar_id")
    Avatar selectedAvatar;
    boolean enabled;
    @Column(name = "created_at")
    OffsetDateTime createdAt;
    @Column(name = "updated_at")
    OffsetDateTime updatedAt;

    @PrePersist
    void p() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
        if (role == null) role = UserRole.CUSTOMER;
        enabled = true;
    }

    @PreUpdate
    void u() {
        updatedAt = OffsetDateTime.now();
    }
}
