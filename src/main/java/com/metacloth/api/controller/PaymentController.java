package com.metacloth.api.controller;

import com.metacloth.api.dto.OrderDtos.CreateOrderRequest;
import com.metacloth.api.dto.PaymentDtos.PaymentOrderInitResponse;
import com.metacloth.api.dto.PaymentDtos.PaymentVerificationResponse;
import com.metacloth.api.dto.PaymentDtos.VerifyPaymentRequest;
import com.metacloth.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping("/create-order")
    public ResponseEntity<PaymentOrderInitResponse> createOrder(
            Authentication auth,
            @Valid @RequestBody CreateOrderRequest request) {
        Long userId = (auth != null && auth.getPrincipal() instanceof Long) ? (Long) auth.getPrincipal() : null;
        PaymentOrderInitResponse res = paymentService.createPaymentOrder(userId, request);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/verify")
    public ResponseEntity<PaymentVerificationResponse> verifyPayment(
            Authentication auth,
            @Valid @RequestBody VerifyPaymentRequest request) {
        Long userId = (auth != null && auth.getPrincipal() instanceof Long) ? (Long) auth.getPrincipal() : null;
        PaymentVerificationResponse res = paymentService.verifyPayment(userId, request);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        if (signature == null || signature.isBlank()) {
            return ResponseEntity.badRequest().body("Missing X-Razorpay-Signature header");
        }
        paymentService.handleWebhook(payload, signature);
        return ResponseEntity.ok("OK");
    }
}
