package com.metacloth.api.controller;

import com.metacloth.api.dto.DtfDtos.DtfDownloadRequest;
import com.metacloth.service.DtfProductionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Isolated DTF on-demand high-resolution download engine.
 * Does NOT touch Order, Cart or Studio core flow.
 * <p>
 * POST /api/v1/dtf/download
 * Body: { original_asset_url, dtf_print_parameters: {selected_sheet_preset, print_width_inches, strip_color_hex, strip_sensitivity, halftone_density, ...} }
 * Processing (Graphics2D bicubic): chroma-key removal + Bayer 4×4 halftone at exactly print_width_inches × 300 DPI.
 * Response: PNG bytes with Content-Disposition: attachment; filename="print_ready_dtf.png"
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dtf")
@RequiredArgsConstructor
public class DtfDownloadController {

    private final DtfProductionService dtfService;

    @PostMapping("/download")
    public ResponseEntity<byte[]> download(@RequestBody DtfDownloadRequest req) {
        if (req == null || (req.original_asset_url() == null || req.original_asset_url().isBlank())
                && (req.dtf_print_parameters() == null
                || (req.dtf_print_parameters().original_asset_url() == null || req.dtf_print_parameters().original_asset_url().isBlank())
                && (req.dtf_print_parameters().preview_data_url() == null || req.dtf_print_parameters().preview_data_url().isBlank()))) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"message\":\"No source image provided\"}".getBytes());
        }
        try {
            byte[] png = dtfService.renderForDownload(req);
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"print_ready_dtf.png\"")
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .contentLength(png.length)
                    .body(png);
        } catch (IllegalArgumentException e) {
            log.warn("DTF download bad request: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(("{\"message\":\"" + e.getMessage().replace("\"","'") + "\"}").getBytes());
        } catch (Exception e) {
            log.error("DTF download failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"message\":\"DTF render failed\"}".getBytes());
        }
    }

    // Health probe — isolated, no auth required via SecurityConfig permit
    @GetMapping("/health")
    public java.util.Map<String,String> health(){
        return java.util.Map.of("status","ok","service","dtf-download");
    }
}
