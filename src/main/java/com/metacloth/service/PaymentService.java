package com.metacloth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.metacloth.api.dto.OrderDtos.CreateOrderRequest;
import com.metacloth.api.dto.OrderDtos.OrderResponse;
import com.metacloth.api.dto.PaymentDtos.PaymentOrderInitResponse;
import com.metacloth.api.dto.PaymentDtos.PaymentVerificationResponse;
import com.metacloth.api.dto.PaymentDtos.VerifyPaymentRequest;
import com.metacloth.config.AppProperties;
import com.metacloth.domain.entity.PaymentOrder;
import com.metacloth.exception.ApiException;
import com.metacloth.repository.OrderRepository;
import com.metacloth.repository.PaymentOrderRepository;
import com.metacloth.repository.UserRepository;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {
    private final AppProperties properties;
    private final OrderService orderService;
    private final PaymentOrderRepository paymentOrders;
    private final OrderRepository orders;
    private final UserRepository users;
    private final ObjectMapper objectMapper;

    @Transactional
    public PaymentOrderInitResponse createPaymentOrder(Long userId, CreateOrderRequest request) {
        BigDecimal total = orderService.calculateVerifiedTotal(request);
        long amountInPaise = total.multiply(BigDecimal.valueOf(100)).longValue();

        String keyId = properties.razorpay().keyId();
        String keySecret = properties.razorpay().keySecret();

        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);
            JSONObject orderReq = new JSONObject();
            orderReq.put("amount", amountInPaise);
            orderReq.put("currency", "INR");
            orderReq.put("receipt", "rcpt_" + UUID.randomUUID().toString().substring(0, 8));

            com.razorpay.Order rzpOrder = client.orders.create(orderReq);
            String rzpOrderId = rzpOrder.get("id");

            String payloadJson = objectMapper.writeValueAsString(request);

            com.metacloth.domain.entity.User user = null;
            if (userId != null) {
                user = users.findById(userId).orElse(null);
            }
            if (user == null && request.shippingPhone() != null && !request.shippingPhone().isBlank()) {
                String phone = request.shippingPhone().trim();
                user = users.findByPhoneNumber(phone)
                        .orElseGet(() -> users.save(com.metacloth.domain.entity.User.builder()
                                .phoneNumber(phone)
                                .fullName(request.shippingName() != null ? request.shippingName().trim() : "MetaCloth Customer")
                                .role(com.metacloth.domain.enums.UserRole.CUSTOMER)
                                .authProvider(com.metacloth.domain.enums.AuthProvider.PHONE)
                                .enabled(true)
                                .build()));
            }

            PaymentOrder po = PaymentOrder.builder()
                    .user(user)
                    .razorpayOrderId(rzpOrderId)
                    .amount(total)
                    .currency("INR")
                    .status("CREATED")
                    .orderPayloadJson(payloadJson)
                    .build();

            paymentOrders.save(po);

            return new PaymentOrderInitResponse(rzpOrderId, amountInPaise, total, "INR", keyId);
        } catch (Exception e) {
            log.error("Failed to initialize Razorpay order: {}", e.getMessage(), e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create payment order: " + e.getMessage());
        }
    }

    @Transactional
    public PaymentVerificationResponse verifyPayment(Long userId, VerifyPaymentRequest request) {
        String keySecret = properties.razorpay().keySecret();
        if (keySecret != null) {
            keySecret = keySecret.trim().replace("\"", "").replace("'", "");
        }

        String rzpOrderId = request.razorpayOrderId() != null ? request.razorpayOrderId().trim() : "";
        String rzpPaymentId = request.razorpayPaymentId() != null ? request.razorpayPaymentId().trim() : "";
        String rzpSignature = request.razorpaySignature() != null ? request.razorpaySignature().trim() : "";

        try {
            JSONObject attrs = new JSONObject();
            attrs.put("razorpay_order_id", rzpOrderId);
            attrs.put("razorpay_payment_id", rzpPaymentId);
            attrs.put("razorpay_signature", rzpSignature);

            boolean valid = Utils.verifyPaymentSignature(attrs, keySecret);
            if (!valid) {
                log.warn("Invalid payment signature for rzpOrderId: {}", rzpOrderId);
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid payment signature");
            }
        } catch (ApiException ae) {
            throw ae;
        } catch (Exception e) {
            log.error("Error verifying payment signature for order {}: {}", rzpOrderId, e.getMessage(), e);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Signature verification failed: " + e.getMessage());
        }

        PaymentOrder po = paymentOrders.findByRazorpayOrderId(rzpOrderId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment record not found"));

        if ("PAID".equals(po.getStatus()) && po.getOrder() != null) {
            return new PaymentVerificationResponse(true, "Payment already verified", orderService.dto(po.getOrder()));
        }

        try {
            CreateOrderRequest orderReq = objectMapper.readValue(po.getOrderPayloadJson(), CreateOrderRequest.class);
            Long effectiveUserId = po.getUser() != null ? po.getUser().getId() : userId;

            OrderResponse orderResponse = orderService.createWithPayment(
                    effectiveUserId,
                    orderReq,
                    rzpOrderId,
                    rzpPaymentId
            );

            po.setStatus("PAID");
            po.setRazorpayPaymentId(rzpPaymentId);
            po.setOrder(orders.getReferenceById(orderResponse.id()));
            paymentOrders.save(po);

            log.info("Payment verified and order created successfully: MC-{}, RZP-Order: {}", orderResponse.orderNumber(), rzpOrderId);
            return new PaymentVerificationResponse(true, "Payment verified successfully", orderResponse);
        } catch (Exception e) {
            log.error("Failed to finalize order after payment verification: {}", e.getMessage(), e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Order placement failed after payment: " + e.getMessage());
        }
    }

    @Transactional
    public void handleWebhook(String payload, String signature) {
        String webhookSecret = properties.razorpay().webhookSecret();

        try {
            boolean valid = Utils.verifyWebhookSignature(payload, signature, webhookSecret);
            if (!valid) {
                log.warn("Invalid Razorpay webhook signature");
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid webhook signature");
            }
        } catch (ApiException ae) {
            throw ae;
        } catch (Exception e) {
            log.error("Webhook signature verification failed: {}", e.getMessage(), e);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Webhook signature error: " + e.getMessage());
        }

        try {
            JSONObject event = new JSONObject(payload);
            String eventType = event.getString("event");

            if ("order.paid".equals(eventType) || "payment.captured".equals(eventType)) {
                JSONObject payloadObj = event.getJSONObject("payload");
                JSONObject paymentEntity = payloadObj.getJSONObject("payment").getJSONObject("entity");
                String rzpOrderId = paymentEntity.optString("order_id");
                String rzpPaymentId = paymentEntity.optString("id");

                if (rzpOrderId != null && !rzpOrderId.isBlank()) {
                    fulfillIfPending(rzpOrderId, rzpPaymentId);
                }
            } else if ("payment.failed".equals(eventType)) {
                JSONObject payloadObj = event.getJSONObject("payload");
                JSONObject paymentEntity = payloadObj.getJSONObject("payment").getJSONObject("entity");
                String rzpOrderId = paymentEntity.optString("order_id");

                if (rzpOrderId != null && !rzpOrderId.isBlank()) {
                    paymentOrders.findByRazorpayOrderId(rzpOrderId).ifPresent(po -> {
                        if (!"PAID".equals(po.getStatus())) {
                            po.setStatus("FAILED");
                            paymentOrders.save(po);
                        }
                    });
                }
            }
        } catch (Exception e) {
            log.error("Failed to process webhook event: {}", e.getMessage(), e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Error handling webhook payload: " + e.getMessage());
        }
    }

    private void fulfillIfPending(String rzpOrderId, String rzpPaymentId) {
        paymentOrders.findByRazorpayOrderId(rzpOrderId).ifPresent(po -> {
            if ("CREATED".equals(po.getStatus())) {
                try {
                    CreateOrderRequest orderReq = objectMapper.readValue(po.getOrderPayloadJson(), CreateOrderRequest.class);
                    Long uid = po.getUser() != null ? po.getUser().getId() : null;
                    OrderResponse orderResponse = orderService.createWithPayment(uid, orderReq, rzpOrderId, rzpPaymentId);
                    po.setStatus("PAID");
                    po.setRazorpayPaymentId(rzpPaymentId);
                    po.setOrder(orders.getReferenceById(orderResponse.id()));
                    paymentOrders.save(po);
                    log.info("Order fulfilled via Razorpay webhook for rzpOrderId: {}, orderNumber: {}", rzpOrderId, orderResponse.orderNumber());
                } catch (Exception e) {
                    log.error("Failed to fulfill order via webhook for {}: {}", rzpOrderId, e.getMessage(), e);
                }
            }
        });
    }
}
