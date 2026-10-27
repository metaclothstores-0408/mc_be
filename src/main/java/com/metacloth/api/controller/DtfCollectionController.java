package com.metacloth.api.controller;

import com.metacloth.api.dto.DtfCollectionDtos.*;
import com.metacloth.exception.ApiException;
import com.metacloth.service.DtfCollectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/dtf-collections")
@RequiredArgsConstructor
public class DtfCollectionController {

    private final DtfCollectionService service;

    @GetMapping
    public List<DtfCollectionResponse> getCollections(
            Authentication a,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestParam(value = "sessionId", required = false) String paramSessionId
    ) {
        Long userId = extractUserId(a, headerUserId);
        String finalSessionId = sessionId != null && !sessionId.isBlank() ? sessionId : paramSessionId;
        return service.getCollections(userId, finalSessionId);
    }

    @GetMapping("/storage-quota")
    public StorageQuotaResponse getStorageQuota(
            Authentication a,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId
    ) {
        Long userId = extractUserId(a, headerUserId);
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required to view storage quota");
        }
        return service.getUserQuota(userId);
    }

    @PostMapping
    public ResponseEntity<DtfCollectionResponse> saveCollection(
            Authentication a,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @Valid @RequestBody SaveDtfCollectionRequest req
    ) {
        Long userId = extractUserId(a, headerUserId);
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Guests must sign in to save DTF designs.");
        }

        String finalSessionId = req.sessionId() != null && !req.sessionId().isBlank()
                ? req.sessionId()
                : sessionId;

        SaveDtfCollectionRequest preparedReq = new SaveDtfCollectionRequest(
                finalSessionId,
                req.designName(),
                req.collectionGroup(),
                req.originalFilename(),
                req.imageDataUrl(),
                req.previewDataUrl(),
                req.selectedSheetPreset(),
                req.printScale(),
                req.stripColorHex(),
                req.stripSensitivity(),
                req.halftoneDensity(),
                req.garmentType(),
                req.fabricColorName(),
                req.fabricColorHex()
        );
        DtfCollectionResponse res = service.saveCollection(userId, preparedReq);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @PutMapping("/{id}")
    public DtfCollectionResponse updateCollection(
            @PathVariable Long id,
            Authentication a,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestParam(value = "sessionId", required = false) String paramSessionId,
            @Valid @RequestBody UpdateDtfCollectionRequest req
    ) {
        Long userId = extractUserId(a, headerUserId);
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Guests must sign in to update DTF designs.");
        }
        String finalSessionId = sessionId != null && !sessionId.isBlank() ? sessionId : paramSessionId;
        return service.updateCollection(id, userId, finalSessionId, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCollection(
            @PathVariable Long id,
            Authentication a,
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId,
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestParam(value = "sessionId", required = false) String paramSessionId
    ) {
        Long userId = extractUserId(a, headerUserId);
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Guests must sign in to delete DTF designs.");
        }
        String finalSessionId = sessionId != null && !sessionId.isBlank() ? sessionId : paramSessionId;
        service.deleteCollection(id, userId, finalSessionId);
        return ResponseEntity.noContent().build();
    }

    private Long extractUserId(Authentication a, String headerUserId) {
        if (a != null && a.isAuthenticated() && a.getPrincipal() instanceof Long id) {
            return id;
        }
        if (headerUserId != null && !headerUserId.isBlank()) {
            try {
                return Long.parseLong(headerUserId.trim());
            } catch (Exception ignored) {}
        }
        return null;
    }
}