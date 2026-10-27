package com.metacloth.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Stores customer saved DTF design collections and versions backed by Cloudflare R2.
 */
@Entity
@Table(name = "dtf_design_collections")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DtfDesignCollection {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    User user;

    @Column(name = "session_id", length = 100)
    String sessionId;

    @Column(name = "design_name", nullable = false)
    String designName;

    @Column(name = "collection_group", length = 100)
    @Builder.Default
    String collectionGroup = "General";

    @Column(name = "original_filename", nullable = false)
    String originalFilename;

    @Column(name = "version_number", nullable = false)
    @Builder.Default
    Integer versionNumber = 1;

    /** Legacy base64 / fallback; nullable now that R2 object storage is active */
    @Column(name = "image_data_url", columnDefinition = "text")
    String imageDataUrl;

    /** Legacy preview data url; nullable */
    @Column(name = "preview_data_url", columnDefinition = "text")
    String previewDataUrl;

    @Column(name = "r2_object_key", length = 500)
    String r2ObjectKey;

    @Column(name = "r2_image_url", length = 1000)
    String r2ImageUrl;

    @Column(name = "r2_preview_key", length = 500)
    String r2PreviewKey;

    @Column(name = "r2_preview_url", length = 1000)
    String r2PreviewUrl;

    @Column(name = "file_size_bytes")
    @Builder.Default
    Long fileSizeBytes = 0L;

    @Column(name = "expires_at")
    OffsetDateTime expiresAt;

    @Column(name = "selected_sheet_preset", length = 10)
    @Builder.Default
    String selectedSheetPreset = "A3";

    @Column(name = "print_scale")
    @Builder.Default
    Double printScale = 1.0;

    @Column(name = "strip_color_hex", length = 7)
    String stripColorHex;

    @Column(name = "strip_sensitivity")
    Integer stripSensitivity;

    @Column(name = "halftone_density")
    Integer halftoneDensity;

    @Column(name = "garment_type", length = 50)
    String garmentType;

    @Column(name = "fabric_color_name", length = 50)
    String fabricColorName;

    @Column(name = "fabric_color_hex", length = 7)
    String fabricColorHex;

    @Column(name = "created_at")
    OffsetDateTime createdAt;

    @Column(name = "updated_at")
    OffsetDateTime updatedAt;

    @PrePersist void prePersist() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
        if (versionNumber == null) versionNumber = 1;
        if (selectedSheetPreset == null) selectedSheetPreset = "A3";
        if (printScale == null) printScale = 1.0;
        if (fileSizeBytes == null) fileSizeBytes = 0L;
        if (expiresAt == null) {
            // Default retention: 14 days for saved collections
            expiresAt = OffsetDateTime.now().plusDays(14);
        }
    }

    @PreUpdate void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
