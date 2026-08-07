package com.metacloth.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public final class OrderDtos {
    public record CreateOrderRequest(@NotBlank String shippingName, @NotBlank String shippingPhone,
                                     @NotBlank String shippingAddressLine, @NotBlank String city,
                                     @NotBlank String postalCode, @NotEmpty List<@Valid ItemRequest> items) {
    }

    public record ItemRequest(@NotNull Long productVariantId, Long avatarId, @Min(1) int quantity,
                              @NotNull BigDecimal unitPrice, String printConfigJson, String previewRenderUrl) {
    }

    public record ItemResponse(Long id, Long productVariantId, Long avatarId, int quantity, BigDecimal unitPrice,
                               String printConfigJson, String previewRenderUrl) {
    }

    public record OrderResponse(Long id, String orderNumber, BigDecimal totalAmount, String status, String shippingName,
                                String shippingPhone, String shippingAddressLine, String city, String postalCode,
                                List<ItemResponse> items) {
    }

    public record StatusUpdateRequest(@NotBlank String status) {
    }

    public record TrackResponse(
            Long id,
            String orderNumber,
            String status,
            List<String> timeline
    ) {
    }
}
