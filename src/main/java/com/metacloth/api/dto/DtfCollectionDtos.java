package com.metacloth.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;

public final class DtfCollectionDtos {
    private DtfCollectionDtos() {}

    public record SaveDtfCollectionRequest(
            String sessionId,
            String designName,
            String collectionGroup,
            @NotBlank String originalFilename,
            @NotBlank String imageDataUrl,
            String previewDataUrl,
            String selectedSheetPreset,
            Double printScale,
            String stripColorHex,
            Integer stripSensitivity,
            Integer halftoneDensity,
            String garmentType,
            String fabricColorName,
            String fabricColorHex
    ) {}

    public record UpdateDtfCollectionRequest(
            String designName,
            String collectionGroup,
            String selectedSheetPreset,
            Double printScale,
            String stripColorHex,
            Integer stripSensitivity,
            Integer halftoneDensity,
            String garmentType,
            String fabricColorName,
            String fabricColorHex,
            String previewDataUrl
    ) {}

    public record DtfCollectionResponse(
            Long id,
            Long userId,
            String sessionId,
            String designName,
            String collectionGroup,
            String originalFilename,
            Integer versionNumber,
            String imageDataUrl,
            String previewDataUrl,
            String selectedSheetPreset,
            Double printScale,
            String stripColorHex,
            Integer stripSensitivity,
            Integer halftoneDensity,
            String garmentType,
            String fabricColorName,
            String fabricColorHex,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {}
}
