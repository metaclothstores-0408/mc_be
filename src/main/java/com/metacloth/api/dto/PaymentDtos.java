package com.metacloth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public final class PaymentDtos {

    public record PaymentOrderInitResponse(
            String razorpayOrderId,
            long amount, // in paise (e.g. 99900 for ₹999.00)
            BigDecimal displayAmount, // in INR (e.g. 999.00)
            String currency,
            String keyId
    ) {
    }

    public record VerifyPaymentRequest(
            @NotBlank String razorpayOrderId,
            @NotBlank String razorpayPaymentId,
            @NotBlank String razorpaySignature
    ) {
    }

    public record PaymentVerificationResponse(
            boolean success,
            String message,
            OrderDtos.OrderResponse order
    ) {
    }
}
