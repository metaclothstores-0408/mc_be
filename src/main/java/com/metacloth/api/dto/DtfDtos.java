package com.metacloth.api.dto;

import java.time.OffsetDateTime;

/**
 * DTOs for DTF production asset status.
 * Independent extension — does not modify OrderDtos.
 */
public final class DtfDtos {
    private DtfDtos(){}

    public record DtfAssetResponse(
            Long id,
            Long orderItemId,
            String originalAssetUrl,
            String processedFilePath,
            String stripColorHex,
            Integer stripSensitivity,
            Integer halftoneDensity,
            java.lang.Double printScale,
            String selectedSheetPreset,
            Double printWidthInches,
            Double sheetWidthInches,
            Double sheetHeightInches,
            String status,
            String errorMessage,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ){}


    // ── Download (on-demand synchronous render) — isolated middleware ──────────

    /**
     * Spec payload for both Download button and checkout:
     * {
     *   original_asset_url: string,
     *   dtf_print_parameters: {
     *     selected_sheet_preset: "A4" | "A3",
     *     print_width_inches: 5.85,
     *     strip_color_hex: "#HEXCODE",
     *     strip_sensitivity: 25,
     *     halftone_density: 40,
     *     // optional extras (backward compatible)
     *     print_scale, sheet_width_inches, sheet_height_inches
     *   }
     * }
     */
    public record DtfDownloadRequest(
            String original_asset_url,
            DtfPrintParams dtf_print_parameters
    ){}

    public record DtfPrintParams(
            String selected_sheet_preset,
            Double print_width_inches,
            String strip_color_hex,
            Integer strip_sensitivity,
            Integer halftone_density,
            // optional extras
            Double print_scale,
            Double sheet_width_inches,
            Double sheet_height_inches,
            String original_asset_url,
            String preview_data_url,
            String source_type
    ){}
}
