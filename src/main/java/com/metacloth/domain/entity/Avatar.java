package com.metacloth.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "avatars")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Avatar {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(nullable = false)
    String title;
    @Column(name = "image_url", nullable = false)
    String imageUrl;
    String category;
    @Column(name = "is_active")
    boolean active;
    @Column(name = "created_at")
    OffsetDateTime createdAt;
}
