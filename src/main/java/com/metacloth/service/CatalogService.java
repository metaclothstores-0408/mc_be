package com.metacloth.service;

import com.metacloth.domain.entity.Product;
import com.metacloth.repository.AvatarRepository;
import com.metacloth.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CatalogService {
    private final AvatarRepository avatars;
    private final ProductRepository products;

    public List<?> avatars() {
        return avatars.findByActiveTrueOrderByIdAsc().stream().map(a -> Map.of("id", a.getId(), "title", a.getTitle(), "imageUrl", a.getImageUrl(), "category", a.getCategory())).toList();
    }

    public List<?> products() {
        return products.findByActiveTrueOrderByIdAsc().stream().map(p -> Map.of("id", p.getId(), "name", p.getName(), "description", p.getDescription(), "garmentType", p.getGarmentType().name(), "basePrice", p.getBasePrice(), "printSurcharge", p.getPrintSurcharge(), "customizable", p.isCustomizable(), "variants", p.getVariants().stream().map(v -> Map.of("id", v.getId(), "colorName", v.getColorName(), "colorHex", v.getColorHex(), "size", v.getSize(), "stockQuantity", v.getStockQuantity(), "mockupFrontUrl", v.getMockupFrontUrl(), "mockupBackUrl", v.getMockupBackUrl())).toList())).toList();
    }

    @Transactional(readOnly = true)
    public Product product(Long id) {

        return products.findById(id)
                .filter(Product::isActive)
                .orElseThrow(() ->
                        new EntityNotFoundException("Product not found"));
    }
}
