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
            String filename = resolveDownloadFilename(req);

            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
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

    private String resolveDownloadFilename(DtfDownloadRequest req) {
        String name = null;
        if (req != null) {
            if (req.original_filename() != null && !req.original_filename().isBlank()) {
                name = req.original_filename();
            } else if (req.dtf_print_parameters() != null && req.dtf_print_parameters().original_filename() != null
                    && !req.dtf_print_parameters().original_filename().isBlank()) {
                name = req.dtf_print_parameters().original_filename();
            }
        }
        if (name == null || name.isBlank()) {
            return "dtf_print_ready_300dpi.png";
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        name = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        return name + "_dtf_300dpi.png";
    }

    @GetMapping("/health")
    public java.util.Map<String,String> health(){
        return java.util.Map.of("status","ok","service","dtf-download");
    }
}
