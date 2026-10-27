package com.metacloth.api.controller;

import com.metacloth.service.R2StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Slf4j
@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
public class StorageController {

    private final R2StorageService r2Storage;

    @GetMapping("/file")
    public ResponseEntity<Void> getFile(@RequestParam("key") String key) {
        if (key == null || key.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        String url = r2Storage.getFileUrl(key.trim());
        if (url == null || url.startsWith("/api/v1/storage/file")) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url))
                .build();
    }
}

