package com.metacloth.api.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/customizer")
@RequiredArgsConstructor
public class CustomizerController {
    @PostMapping("/preview")
    Map<String, Object> preview(@Valid @RequestBody Preview p) {
        return Map.of("productVariantId", p.productVariantId(), "avatarId", p.avatarId(), "printZone", p.printZone(), "x", p.x(), "y", p.y(), "scale", p.scale(), "previewRenderUrl", p.previewRenderUrl());
    }

    record Preview(@NotNull Long productVariantId, Long avatarId, @NotBlank String printZone, @NotNull Double x,
                   @NotNull Double y, @NotNull Double scale, String previewRenderUrl) {
    }
}
