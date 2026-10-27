package com.metacloth.api.dto;

import java.time.OffsetDateTime;

/**
 * DTOs for DTF production asset status.
 * Independent extension - does not modify OrderDtos.
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
            Double printScale,
            String selectedSheetPreset,
            Double printWidthInches,
            Double sheetWidthInches,
            Double sheetHeightInches,
            String status,
            String errorMessage,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ){}

    public record DtfDownloadRequest(
            String original_asset_url,
            String original_filename,
            DtfPrintParams dtf_print_parameters
    ){}

    public record DtfPrintParams(
            String selected_sheet_preset,
            Double print_width_inches,
            String strip_color_hex,
            Integer strip_sensitivity,
            Integer halftone_density,
            Double print_scale,
            Double sheet_width_inches,
            Double sheet_height_inches,
            String original_asset_url,
            String original_filename,
            String preview_data_url,
            String source_type
    ){}
}
