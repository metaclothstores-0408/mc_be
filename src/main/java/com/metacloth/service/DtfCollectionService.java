package com.metacloth.service;

import com.metacloth.api.dto.DtfCollectionDtos.*;
import com.metacloth.domain.entity.DtfDesignCollection;
import com.metacloth.domain.entity.User;
import com.metacloth.exception.ApiException;
import com.metacloth.repository.DtfDesignCollectionRepository;
import com.metacloth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DtfCollectionService {

    private final DtfDesignCollectionRepository collections;
    private final UserRepository users;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    @Transactional(readOnly = true)
    public List<DtfCollectionResponse> getCollections(Long userId, String sessionId) {
        if (userId == null && (sessionId == null || sessionId.isBlank())) {
            return List.of();
        }
        return collections.findForUserOrSession(userId, sessionId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public DtfCollectionResponse saveCollection(Long userId, SaveDtfCollectionRequest req) {
        User user = null;
        if (userId != null) {
            user = users.findById(userId).orElse(null);
        }

        String filename = req.originalFilename();
        if (filename == null || filename.isBlank()) {
            filename = "graphic.png";
        }

        int latestVersion = collections.findLatestVersionNumber(filename, userId, req.sessionId());
        int nextVersion = latestVersion + 1;

        String designName = req.designName();
        if (designName == null || designName.isBlank()) {
            designName = formatAutoDesignName(filename, nextVersion);
        }

        DtfDesignCollection entity = DtfDesignCollection.builder()
                .user(user)
                .sessionId(req.sessionId())
                .designName(designName)
                .originalFilename(filename)
                .versionNumber(nextVersion)
                .imageDataUrl(req.imageDataUrl())
                .previewDataUrl(req.previewDataUrl())
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
        if (req.selectedSheetPreset() != null) entity.setSelectedSheetPreset(req.selectedSheetPreset());
        if (req.printScale() != null) entity.setPrintScale(req.printScale());
        if (req.stripColorHex() != null) entity.setStripColorHex(req.stripColorHex());
        if (req.stripSensitivity() != null) entity.setStripSensitivity(req.stripSensitivity());
        if (req.halftoneDensity() != null) entity.setHalftoneDensity(req.halftoneDensity());
        if (req.garmentType() != null) entity.setGarmentType(req.garmentType());
        if (req.fabricColorName() != null) entity.setFabricColorName(req.fabricColorName());
        if (req.fabricColorHex() != null) entity.setFabricColorHex(req.fabricColorHex());
        if (req.previewDataUrl() != null) entity.setPreviewDataUrl(req.previewDataUrl());

        DtfDesignCollection updated = collections.save(entity);
        return toDto(updated);
    }

    @Transactional
    public void deleteCollection(Long id, Long userId, String sessionId) {
        DtfDesignCollection entity = collections.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DTF collection design not found"));

        validateOwnership(entity, userId, sessionId);
        collections.delete(entity);
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
        return base + " · v" + versionNumber + " · " + timeStr;
    }

    private DtfCollectionResponse toDto(DtfDesignCollection d) {
        return new DtfCollectionResponse(
                d.getId(),
                d.getUser() != null ? d.getUser().getId() : null,
                d.getSessionId(),
                d.getDesignName(),
                d.getOriginalFilename(),
                d.getVersionNumber(),
                d.getImageDataUrl(),
                d.getPreviewDataUrl(),
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
