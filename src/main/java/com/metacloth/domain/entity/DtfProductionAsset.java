package com.metacloth.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Independent DTF production asset - stores headless-generated 300 DPI PNG
 * for the printing machine, backed by Cloudflare R2 object storage.
 */
@Entity
@Table(name = "dtf_production_assets")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DtfProductionAsset {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id", nullable = false)
    OrderItem orderItem;

    @Column(name = "original_asset_url", columnDefinition = "text")
    String originalAssetUrl;

    /** Absolute path on server or legacy file path */
    @Column(name = "processed_file_path", length = 1000)
    String processedFilePath;

    @Column(name = "r2_object_key", length = 500)
    String r2ObjectKey;

    @Column(name = "r2_image_url", length = 1000)
    String r2ImageUrl;

    @Column(name = "file_size_bytes")
    @Builder.Default
    Long fileSizeBytes = 0L;

    @Column(name = "expires_at")
    OffsetDateTime expiresAt;

    @Column(name = "strip_color_hex", length = 7)
    String stripColorHex;

    @Column(name = "strip_sensitivity")
    Integer stripSensitivity;

    @Column(name = "halftone_density")
    Integer halftoneDensity;

    /** Print size multiplier 0.4..4.0 applied to decal plane and 300 DPI output */
    @Column(name = "print_scale")
    Double printScale;

    /** Physical sheet preset A3/A4 */
    @Column(name = "selected_sheet_preset", length = 10)
    String selectedSheetPreset;

    /** Absolute physical width in inches at 300 DPI */
    @Column(name = "print_width_inches")
    Double printWidthInches;

    @Column(name = "sheet_width_inches")
    Double sheetWidthInches;

    @Column(name = "sheet_height_inches")
    Double sheetHeightInches;

    /** PENDING | PROCESSING | READY | FAILED | ARCHIVED_PURGED */
    @Column(name = "status", length = 20)
    @Builder.Default
    String status = "PENDING";

    @Column(name = "error_message", length = 1000)
    String errorMessage;

    @Column(name = "created_at")
    OffsetDateTime createdAt;

    @Column(name = "updated_at")
    OffsetDateTime updatedAt;

    @PrePersist void prePersist() {
        createdAt = OffsetDateTime.now();
        updatedAt = createdAt;
        if (status == null) status = "PENDING";
        if (fileSizeBytes == null) fileSizeBytes = 0L;
        if (expiresAt == null) {
            // Default retention: 30 days for production print assets
            expiresAt = OffsetDateTime.now().plusDays(30);
        }
    }

    @PreUpdate void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
