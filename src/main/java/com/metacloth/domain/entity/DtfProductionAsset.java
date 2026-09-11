package com.metacloth.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Independent DTF production asset — stores headless-generated 300 DPI PNG
 * for the printing machine. Isolated from core Order/OrderItem flow.
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

    @Column(name = "original_asset_url", length = 1000)
    String originalAssetUrl;

    /** Absolute path on server / object-storage key for print-ready PNG */
    @Column(name = "processed_file_path", length = 1000)
    String processedFilePath;

    @Column(name = "strip_color_hex", length = 7)
    String stripColorHex;

    @Column(name = "strip_sensitivity")
    Integer stripSensitivity;

    @Column(name = "halftone_density")
    Integer halftoneDensity;

    /** Print size multiplier 0.4..4.0 applied to decal plane and 300 DPI output */
    @Column(name = "print_scale")
    Double printScale;

    /** Physical sheet preset A3/A4 — garment-invariant frame (S–XXL same transfer) */
    @Column(name = "selected_sheet_preset", length = 10)
    String selectedSheetPreset;

    /** Absolute physical width in inches at 300 DPI (e.g. A3 @ 50% = 5.85 → 1755 px) */
    @Column(name = "print_width_inches")
    Double printWidthInches;

    @Column(name = "sheet_width_inches")
    Double sheetWidthInches;

    @Column(name = "sheet_height_inches")
    Double sheetHeightInches;

    /** PENDING | PROCESSING | READY | FAILED */
    @Column(name = "status", length = 20)
    @Builder.Default
    String status = "PENDING";

    @Column(name = "error_message", length = 1000)
    String errorMessage;

    @Column(name = "created_at")
    OffsetDateTime createdAt;

    @Column(name = "updated_at")
    OffsetDateTime updatedAt;

    @PrePersist void prePersist(){ createdAt=OffsetDateTime.now(); updatedAt=createdAt; if(status==null) status="PENDING"; }
    @PreUpdate void preUpdate(){ updatedAt=OffsetDateTime.now(); }
}
