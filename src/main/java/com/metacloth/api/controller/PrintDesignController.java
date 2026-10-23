package com.metacloth.api.controller;

import com.metacloth.service.ImageEnhancerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping({"/api/dtf", "/api/v1/dtf"})
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000", "*"})
@RequiredArgsConstructor
public class PrintDesignController {

    private final ImageEnhancerService enhancerService;

    @PostMapping(value = "/process", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> process(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "enableEnhance", defaultValue = "false") boolean enableEnhance,
            @RequestParam(value = "removeBg", defaultValue = "false") boolean removeBg,
            @RequestParam(value = "isPhoto", defaultValue = "false") boolean isPhoto) {
        try {
            log.info("Processing DTF print request: filename={}, size={}, enhance={}, removeBg={}, isPhoto={}",
                    file != null ? file.getOriginalFilename() : "null",
                    file != null ? file.getSize() : 0,
                    enableEnhance, removeBg, isPhoto);

            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            byte[] result = enhancerService.processDtfPrint(file, enableEnhance, removeBg, isPhoto);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"dtf_output_300dpi.png\"")
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(result);
        } catch (Exception e) {
            log.error("DTF enhancement processing failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(("{\"message\":\"" + e.getMessage().replace("\"", "'") + "\"}").getBytes());
        }
    }
}
