package com.metacloth.api.controller;

import com.metacloth.domain.entity.Avatar;
import com.metacloth.domain.entity.Product;
import com.metacloth.domain.entity.ProductVariant;
import com.metacloth.domain.enums.GarmentType;
import com.metacloth.repository.AvatarRepository;
import com.metacloth.repository.ProductRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/catalog")
@RequiredArgsConstructor
public class AdminCatalogController {

    private final ProductRepository productRepository;
    private final AvatarRepository avatarRepository;

    @PostMapping("/products")
    public Product createProduct(
            @Valid @RequestBody CreateProductRequest request
    ) {

        Product product = Product.builder()
                .name(request.name())
                .description(request.description())
                .garmentType(request.garmentType())
                .basePrice(request.basePrice())
                .printSurcharge(
                        request.printSurcharge() == null
                                ? BigDecimal.ZERO
                                : request.printSurcharge()
                )
                .customizable(request.customizable())
                .active(true)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        Product saved = productRepository.save(product);

        if (request.variants() != null) {

            for (VariantRequest variantRequest : request.variants()) {

                ProductVariant variant = ProductVariant.builder()
                        .product(saved)
                        .colorName(variantRequest.colorName())
                        .colorHex(variantRequest.colorHex())
                        .size(variantRequest.size())
                        .stockQuantity(variantRequest.stockQuantity())
                        .mockupFrontUrl(variantRequest.mockupFrontUrl())
                        .mockupBackUrl(variantRequest.mockupBackUrl())
                        .createdAt(OffsetDateTime.now())
                        .updatedAt(OffsetDateTime.now())
                        .build();

                saved.getVariants().add(variant);
            }
        }

        return productRepository.save(saved);
    }

    @PostMapping("/avatars")
    public Avatar createAvatar(
            @Valid @RequestBody CreateAvatarRequest request
    ) {

        Avatar avatar = Avatar.builder()
                .title(request.title())
                .imageUrl(request.imageUrl())
                .category(
                        request.category() == null
                                ? "CUSTOM"
                                : request.category()
                )
                .active(true)
                .createdAt(OffsetDateTime.now())
                .build();

        return avatarRepository.save(avatar);
    }

    @PatchMapping("/products/{id}/active")
    public Product updateProductStatus(
            @PathVariable Long id,
            @RequestParam boolean active
    ) {

        Product product = productRepository.findById(id)
                .orElseThrow();

        product.setActive(active);

        return productRepository.save(product);
    }

    @PatchMapping("/avatars/{id}/active")
    public Avatar updateAvatarStatus(
            @PathVariable Long id,
            @RequestParam boolean active
    ) {

        Avatar avatar = avatarRepository.findById(id)
                .orElseThrow();

        avatar.setActive(active);

        return avatarRepository.save(avatar);
    }

    public record CreateProductRequest(

            @NotBlank
            String name,

            String description,

            @NotNull
            GarmentType garmentType,

            @NotNull
            @PositiveOrZero
            BigDecimal basePrice,

            @PositiveOrZero
            BigDecimal printSurcharge,

            boolean customizable,

            List<VariantRequest> variants
    ) {}

    public record VariantRequest(

            @NotBlank
            String colorName,

            @NotBlank
            String colorHex,

            @NotBlank
            String size,

            @PositiveOrZero
            int stockQuantity,

            @NotBlank
            String mockupFrontUrl,

            String mockupBackUrl
    ) {}

    public record CreateAvatarRequest(

            @NotBlank
            String title,

            @NotBlank
            String imageUrl,

            String category
    ) {}
}