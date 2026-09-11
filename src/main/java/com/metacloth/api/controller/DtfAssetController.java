package com.metacloth.api.controller;

import com.metacloth.api.dto.DtfDtos.DtfAssetResponse;
import com.metacloth.domain.entity.DtfProductionAsset;
import com.metacloth.exception.ApiException;
import com.metacloth.repository.DtfProductionAssetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;

/**
 * Isolated DTF asset endpoints — does not affect existing order flow.
 * - GET /api/v1/orders/{orderId}/items/{itemId}/dtf-asset  -> JSON status
 * - GET /api/v1/orders/{orderId}/items/{itemId}/dtf-file   -> PNG file (if READY)
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class DtfAssetController {

    private final DtfProductionAssetRepository assets;

    @GetMapping("/{orderId}/items/{itemId}/dtf-asset")
    public DtfAssetResponse getAsset(@PathVariable Long orderId, @PathVariable Long itemId) {
        DtfProductionAsset a = assets.findByOrderItemId(itemId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DTF asset not found"));
        // optional order ownership check could be added here if needed
        return toDto(a);
    }

    @GetMapping("/{orderId}/items/{itemId}/dtf-file")
    public ResponseEntity<Resource> getFile(@PathVariable Long orderId, @PathVariable Long itemId) {
        DtfProductionAsset a = assets.findByOrderItemId(itemId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DTF asset not found"));
        if (!"READY".equals(a.getStatus()) || a.getProcessedFilePath() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "DTF asset not ready: " + a.getStatus());
        }
        File f = new File(a.getProcessedFilePath());
        if (!f.exists()) throw new ApiException(HttpStatus.NOT_FOUND, "DTF file missing on server");
        Resource res = new FileSystemResource(f);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"dtf_" + itemId + ".png\"")
                .body(res);
    }

    private DtfAssetResponse toDto(DtfProductionAsset a) {
        return new DtfAssetResponse(
                a.getId(), a.getOrderItem().getId(), a.getOriginalAssetUrl(),
                a.getProcessedFilePath(), a.getStripColorHex(), a.getStripSensitivity(),
                a.getHalftoneDensity(), a.getPrintScale(),
                a.getSelectedSheetPreset(), a.getPrintWidthInches(),
                a.getSheetWidthInches(), a.getSheetHeightInches(),
                a.getStatus(), a.getErrorMessage(),
                a.getCreatedAt(), a.getUpdatedAt()
        );
    }
}
