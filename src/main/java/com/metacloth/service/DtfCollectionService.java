package com.metacloth.service;

import com.metacloth.api.dto.DtfCollectionDtos.*;
import com.metacloth.domain.entity.DtfDesignCollection;
import com.metacloth.domain.entity.User;
import com.metacloth.exception.ApiException;
import com.metacloth.repository.DtfDesignCollectionRepository;
import com.metacloth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DtfCollectionService {

    private final DtfDesignCollectionRepository collections;
    private final UserRepository users;
    private final R2StorageService r2Storage;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    @Transactional(readOnly = true)
    public List<DtfCollectionResponse> getCollections(Long userId, String sessionId) {
        List<DtfDesignCollection> list;
        if (userId != null) {
            list = collections.findByUserIdOrderByUpdatedAtDesc(userId);
        } else if (sessionId != null && !sessionId.isBlank()) {
            list = collections.findBySessionIdOrderByUpdatedAtDesc(sessionId);
        } else {
            return List.of();
        }
        return list.stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public StorageQuotaResponse getUserQuota(Long userId) {
        if (userId == null) {
            return new StorageQuotaResponse(0L, R2StorageService.USER_STORAGE_QUOTA_BYTES, 0.0, 60.0, 0.0);
        }
        long used = collections.calculateUserStorageBytes(userId);
        long quota = R2StorageService.USER_STORAGE_QUOTA_BYTES;
        double usedMb = Math.round((used / (1024.0 * 1024.0)) * 100.0) / 100.0;
        double quotaMb = Math.round((quota / (1024.0 * 1024.0)) * 100.0) / 100.0;
        double pct = Math.round(((double) used / quota) * 10000.0) / 100.0;
        return new StorageQuotaResponse(used, quota, usedMb, quotaMb, pct);
    }

    @Transactional
    public DtfCollectionResponse saveCollection(Long userId, SaveDtfCollectionRequest req) {
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Please sign in or create an account to save DTF designs.");
        }

        User user = users.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));

        String filename = req.originalFilename();
        if (filename == null || filename.isBlank()) {
            filename = "graphic.png";
        }

        // Parse artwork image data
        byte[] imageBytes = parseDataUrl(req.imageDataUrl());
        if (imageBytes.length == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid image artwork data provided");
        }

        // Enforce 60 MB user quota before saving
        long currentUsage = collections.calculateUserStorageBytes(userId);
        if (currentUsage + imageBytes.length > R2StorageService.USER_STORAGE_QUOTA_BYTES) {
            double currentMb = Math.round((currentUsage / (1024.0 * 1024.0)) * 10.0) / 10.0;
            double incomingMb = Math.round((imageBytes.length / (1024.0 * 1024.0)) * 10.0) / 10.0;
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "User storage quota exceeded (60 MB limit). Current usage: " + currentMb + " MB, Incoming artwork: "
                            + incomingMb + " MB. Please delete older versions to free space.");
        }

        int latestVersion = (userId != null)
                ? collections.findLatestVersionForUser(filename, userId)
                : (req.sessionId() != null ? collections.findLatestVersionForSession(filename, req.sessionId()) : 0);
        int nextVersion = latestVersion + 1;

        String designName = req.designName();
        if (designName == null || designName.isBlank()) {
            designName = formatAutoDesignName(filename, nextVersion);
        }

        String group = req.collectionGroup();
        if (group == null || group.isBlank()) {
            group = "General";
        }

        // Upload main artwork to R2
        String sanitizedName = sanitizeFilename(filename);
        long timestamp = System.currentTimeMillis();
        String artworkKey = "users/" + userId + "/collections/" + timestamp + "_" + sanitizedName;
        String contentType = extractMimeType(req.imageDataUrl());
        String r2ImageUrl = r2Storage.uploadBytes(artworkKey, imageBytes, contentType);

        // Upload thumbnail preview to R2 if provided
        String previewKey = null;
        String r2PreviewUrl = null;
        if (req.previewDataUrl() != null && !req.previewDataUrl().isBlank()) {
            byte[] previewBytes = parseDataUrl(req.previewDataUrl());
            if (previewBytes.length > 0) {
                previewKey = "users/" + userId + "/collections/" + timestamp + "_thumb_" + sanitizedName;
                r2PreviewUrl = r2Storage.uploadBytes(previewKey, previewBytes, extractMimeType(req.previewDataUrl()));
            }
        }

        // Persist metadata to database without raw base64 bloat
        DtfDesignCollection entity = DtfDesignCollection.builder()
                .user(user)
                .sessionId(req.sessionId())
                .designName(designName)
                .collectionGroup(group)
                .originalFilename(filename)
                .versionNumber(nextVersion)
                .imageDataUrl(null) // No Base64 stored in database!
                .previewDataUrl(null)
                .r2ObjectKey(artworkKey)
                .r2ImageUrl(r2ImageUrl)
                .r2PreviewKey(previewKey)
                .r2PreviewUrl(r2PreviewUrl)
                .fileSizeBytes((long) imageBytes.length)
                .expiresAt(OffsetDateTime.now().plusDays(14)) // Auto-expires after 14 days
                .selectedSheetPreset(req.selectedSheetPreset() != null ? req.selectedSheetPreset() : "A3")
                .printScale(req.printScale() != null ? req.printScale() : 1.0)
                .stripColorHex(req.stripColorHex())
                .stripSensitivity(req.stripSensitivity())
                .halftoneDensity(req.halftoneDensity())
                .garmentType(req.garmentType() != null ? req.garmentType() : "OVERSIZED_BOXY")
                .fabricColorName(req.fabricColorName() != null ? req.fabricColorName() : "Noir Black")
                .fabricColorHex(req.fabricColorHex() != null ? req.fabricColorHex() : "#0B0B0C")
                .build();

        DtfDesignCollection saved = collections.save(entity);
        log.info("Saved collection #{} ({}) to R2 key={} size={} bytes for user #{}",
                saved.getId(), saved.getDesignName(), artworkKey, imageBytes.length, userId);

        return toDto(saved);
    }

    @Transactional
    public DtfCollectionResponse updateCollection(Long id, Long userId, String sessionId, UpdateDtfCollectionRequest req) {
        DtfDesignCollection entity = collections.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DTF collection design not found"));

        validateOwnership(entity, userId, sessionId);

        if (req.designName() != null && !req.designName().isBlank()) {
            entity.setDesignName(req.designName());
        }
        if (req.collectionGroup() != null && !req.collectionGroup().isBlank()) {
            entity.setCollectionGroup(req.collectionGroup());
        }
        if (req.selectedSheetPreset() != null) entity.setSelectedSheetPreset(req.selectedSheetPreset());
        if (req.printScale() != null) entity.setPrintScale(req.printScale());
        if (req.stripColorHex() != null) entity.setStripColorHex(req.stripColorHex());
        if (req.stripSensitivity() != null) entity.setStripSensitivity(req.stripSensitivity());
        if (req.halftoneDensity() != null) entity.setHalftoneDensity(req.halftoneDensity());
        if (req.garmentType() != null) entity.setGarmentType(req.garmentType());
        if (req.fabricColorName() != null) entity.setFabricColorName(req.fabricColorName());
        if (req.fabricColorHex() != null) entity.setFabricColorHex(req.fabricColorHex());

        // Update preview in R2 if provided
        if (req.previewDataUrl() != null && !req.previewDataUrl().isBlank() && req.previewDataUrl().startsWith("data:")) {
            byte[] previewBytes = parseDataUrl(req.previewDataUrl());
            if (previewBytes.length > 0) {
                String prevKey = entity.getR2PreviewKey();
                if (prevKey == null) {
                    prevKey = "users/" + (userId != null ? userId : "anon") + "/collections/"
                            + System.currentTimeMillis() + "_thumb_" + sanitizeFilename(entity.getOriginalFilename());
                }
                String r2PrevUrl = r2Storage.uploadBytes(prevKey, previewBytes, extractMimeType(req.previewDataUrl()));
                entity.setR2PreviewKey(prevKey);
                entity.setR2PreviewUrl(r2PrevUrl);
            }
        }

        DtfDesignCollection updated = collections.save(entity);
        return toDto(updated);
    }

    @Transactional
    public void deleteCollection(Long id, Long userId, String sessionId) {
        DtfDesignCollection entity = collections.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DTF collection design not found"));

        validateOwnership(entity, userId, sessionId);

        // Delete from R2 object storage to maintain 10 GB free tier
        if (entity.getR2ObjectKey() != null) {
            r2Storage.deleteObject(entity.getR2ObjectKey());
        }
        if (entity.getR2PreviewKey() != null) {
            r2Storage.deleteObject(entity.getR2PreviewKey());
        }

        collections.delete(entity);
        log.info("Deleted collection #{} and cleaned up associated R2 objects", id);
    }

    /**
     * Extends retention when item is placed in active cart (+14 days).
     */
    @Transactional
    public void extendRetentionForCart(Long id) {
        collections.findById(id).ifPresent(entity -> {
            OffsetDateTime currentExpiry = entity.getExpiresAt() != null ? entity.getExpiresAt() : OffsetDateTime.now();
            entity.setExpiresAt(currentExpiry.plusDays(14));
            collections.save(entity);
            log.info("Extended retention for collection #{} in cart to {}", id, entity.getExpiresAt());
        });
    }

    private void validateOwnership(DtfDesignCollection entity, Long userId, String sessionId) {
        if (userId != null && entity.getUser() != null && entity.getUser().getId().equals(userId)) {
            return;
        }
        if (sessionId != null && !sessionId.isBlank() && sessionId.equals(entity.getSessionId())) {
            return;
        }
        throw new ApiException(HttpStatus.FORBIDDEN, "Access denied to this design collection");
    }

    private String formatAutoDesignName(String originalFilename, int versionNumber) {
        String base = originalFilename;
        int dotIdx = base.lastIndexOf('.');
        if (dotIdx > 0) {
            base = base.substring(0, dotIdx);
        }
        String timeStr = DATE_FORMATTER.format(OffsetDateTime.now());
        return base + " - v" + versionNumber + " - " + timeStr;
    }

    private byte[] parseDataUrl(String dataUrl) {
        if (dataUrl == null || dataUrl.isBlank()) return new byte[0];
        try {
            int commaIdx = dataUrl.indexOf(',');
            String base64 = (commaIdx >= 0) ? dataUrl.substring(commaIdx + 1) : dataUrl;
            return Base64.getDecoder().decode(base64.trim());
        } catch (Exception e) {
            log.warn("Failed to decode dataUrl: {}", e.getMessage());
            return new byte[0];
        }
    }

    private String extractMimeType(String dataUrl) {
        if (dataUrl != null && dataUrl.startsWith("data:") && dataUrl.contains(";")) {
            return dataUrl.substring(5, dataUrl.indexOf(';'));
        }
        return "image/png";
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "artwork.png";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private DtfCollectionResponse toDto(DtfDesignCollection d) {
        // Resolve URLs: prefer fresh presigned R2 URLs when R2 object key is available
        String imageUrl = null;
        if (d.getR2ObjectKey() != null && !d.getR2ObjectKey().isBlank() && r2Storage.isConfigured()) {
            imageUrl = r2Storage.getFileUrl(d.getR2ObjectKey());
        }
        if (imageUrl == null) {
            imageUrl = d.getR2ImageUrl();
        }
        if (imageUrl == null) {
            imageUrl = d.getImageDataUrl(); // legacy fallback
        }

        String previewUrl = null;
        if (d.getR2PreviewKey() != null && !d.getR2PreviewKey().isBlank() && r2Storage.isConfigured()) {
            previewUrl = r2Storage.getFileUrl(d.getR2PreviewKey());
        }
        if (previewUrl == null) {
            previewUrl = d.getR2PreviewUrl();
        }
        if (previewUrl == null) {
            previewUrl = d.getPreviewDataUrl(); // legacy fallback
        }

        return new DtfCollectionResponse(
                d.getId(),
                d.getUser() != null ? d.getUser().getId() : null,
                d.getSessionId(),
                d.getDesignName(),
                d.getCollectionGroup() != null ? d.getCollectionGroup() : "General",
                d.getOriginalFilename(),
                d.getVersionNumber(),
                imageUrl,
                previewUrl,
                d.getR2ImageUrl(),
                d.getR2PreviewUrl(),
                d.getFileSizeBytes() != null ? d.getFileSizeBytes() : 0L,
                d.getExpiresAt(),
                d.getSelectedSheetPreset(),
                d.getPrintScale(),
                d.getStripColorHex(),
                d.getStripSensitivity(),
                d.getHalftoneDensity(),
                d.getGarmentType(),
                d.getFabricColorName(),
                d.getFabricColorHex(),
                d.getCreatedAt(),
                d.getUpdatedAt()
        );
    }
}
