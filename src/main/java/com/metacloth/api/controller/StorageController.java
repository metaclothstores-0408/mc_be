package com.metacloth.api.controller;

import com.metacloth.service.R2StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
public class StorageController {

    private final R2StorageService r2Storage;

    @GetMapping("/file")
    public ResponseEntity<?> getFile(@RequestParam("key") String key) {
        if (key == null || key.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (r2Storage.isConfigured()) {
            String url = r2Storage.getFileUrl(key.trim());
            if (url != null && !url.startsWith("/api/v1/storage/file")) {
                return ResponseEntity.status(HttpStatus.FOUND)
                        .location(URI.create(url))
                        .build();
            }
        }
        Path local = Paths.get("storage", key.trim());
        if (Files.exists(local)) {
            try {
                Resource resource = new FileSystemResource(local.toFile());
                String contentType = Files.probeContentType(local);
                if (contentType == null) contentType = "image/png";
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .body(resource);
            } catch (Exception e) {
                log.warn("Failed to read local file {}: {}", key, e.getMessage());
            }
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "artworks") String folder) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }
        try {
            String orig = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
            String ext = "png";
            if (orig.endsWith(".jpg") || orig.endsWith(".jpeg")) ext = "jpg";
            else if (orig.endsWith(".webp")) ext = "webp";
            else if (orig.endsWith(".svg")) ext = "svg";

            String objectKey = folder + "/" + UUID.randomUUID() + "." + ext;
            String contentType = file.getContentType() != null ? file.getContentType() : "image/png";

            String url = r2Storage.uploadBytes(objectKey, file.getBytes(), contentType);
            return ResponseEntity.ok(Map.of(
                    "url", url,
                    "key", objectKey,
                    "fileSize", file.getSize(),
                    "contentType", contentType
            ));
        } catch (Exception e) {
            log.error("Upload to storage failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Storage upload failed: " + e.getMessage()));
        }
    }

    @PostMapping(value = "/upload-data-url", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> uploadDataUrl(
            @RequestBody Map<String, String> request) {
        String dataUrl = request.get("dataUrl");
        if (dataUrl == null || dataUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing dataUrl"));
        }
        String folder = request.getOrDefault("folder", "artworks");
        try {
            int comma = dataUrl.indexOf(',');
            String b64 = comma > 0 ? dataUrl.substring(comma + 1) : dataUrl;
            byte[] bytes = Base64.getDecoder().decode(b64.replaceAll("\\s+", ""));

            String contentType = "image/png";
            String ext = "png";
            if (dataUrl.startsWith("data:")) {
                int semi = dataUrl.indexOf(';');
                if (semi > 5) {
                    contentType = dataUrl.substring(5, semi);
                    if (contentType.contains("jpeg") || contentType.contains("jpg")) ext = "jpg";
                    else if (contentType.contains("webp")) ext = "webp";
                }
            }

            String objectKey = folder + "/" + UUID.randomUUID() + "." + ext;
            String url = r2Storage.uploadBytes(objectKey, bytes, contentType);
            return ResponseEntity.ok(Map.of(
                    "url", url,
                    "key", objectKey,
                    "fileSize", bytes.length,
                    "contentType", contentType
            ));
        } catch (Exception e) {
            log.error("Upload dataUrl to storage failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Storage upload failed: " + e.getMessage()));
        }
    }
}
