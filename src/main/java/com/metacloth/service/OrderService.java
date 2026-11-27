package com.metacloth.service;

import com.metacloth.api.dto.OrderDtos.CreateOrderRequest;
import com.metacloth.api.dto.OrderDtos.ItemRequest;
import com.metacloth.api.dto.OrderDtos.ItemResponse;
import com.metacloth.api.dto.OrderDtos.OrderResponse;
import com.metacloth.domain.entity.*;
import com.metacloth.domain.enums.OrderStatus;
import com.metacloth.exception.ApiException;
import com.metacloth.repository.AvatarRepository;
import com.metacloth.repository.OrderRepository;
import com.metacloth.repository.ProductVariantRepository;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orders;
    private final UserService users;
    private final ProductVariantRepository variants;
    private final AvatarRepository avatars;
    private final DtfProductionService dtfProductionService;

    @Transactional
    public OrderResponse create(Long uid, CreateOrderRequest r) {
        return createWithPayment(uid, r, null, null);
    }

    @Transactional
    public OrderResponse createWithPayment(Long uid, CreateOrderRequest r, String razorpayOrderId, String razorpayPaymentId) {
        User u = null;
        if (uid != null) {
            u = users.customer(uid);
        } else if (r.shippingPhone() != null && !r.shippingPhone().isBlank()) {
            String phone = r.shippingPhone().trim();
            u = users.phoneUser(phone);
        }
        Order o = Order.builder()
                .orderNumber("MC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .user(u)
                .shippingName(r.shippingName())
                .shippingPhone(r.shippingPhone())
                .shippingAddressLine(r.shippingAddressLine())
                .city(r.city())
                .postalCode(r.postalCode())
                .orderStatus(OrderStatus.PLACED)
                .razorpayOrderId(razorpayOrderId)
                .razorpayPaymentId(razorpayPaymentId)
                .totalAmount(BigDecimal.ZERO)
                .build();
        BigDecimal total = BigDecimal.ZERO;
        for (ItemRequest i : r.items()) {
            ProductVariant v = variants.findByIdForUpdate(i.productVariantId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Variant not found"));
            if (v.getStockQuantity() < i.quantity()) throw new ApiException(HttpStatus.CONFLICT, "Insufficient stock");
            BigDecimal expected = v.getProduct().getBasePrice();
            boolean hasCustomDesign = (i.avatarId() != null)
                    || (i.printConfigJson() != null && !i.printConfigJson().isBlank() && !i.printConfigJson().trim().equals("{}"));
            if (v.getProduct().isCustomizable() && hasCustomDesign)
                expected = expected.add(v.getProduct().getPrintSurcharge());
            if (i.unitPrice().compareTo(expected) != 0)
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid unit price");
            v.setStockQuantity(v.getStockQuantity() - i.quantity());
            Avatar a = i.avatarId() == null ? null : avatars.findById(i.avatarId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Avatar not found"));
            o.getItems().add(OrderItem.builder().order(o).productVariant(v).avatar(a).quantity(i.quantity()).unitPrice(expected).printConfigJson(i.printConfigJson()).previewRenderUrl(i.previewRenderUrl()).build());
            total = total.add(expected.multiply(BigDecimal.valueOf(i.quantity())));
        }
        o.setTotalAmount(total);
        Order saved = orders.save(o);
        // --- Isolated async DTF production pipeline (non-blocking) ---
        try {
            dtfProductionService.processOrderAsync(saved.getId(), saved.getItems());
        } catch (Exception e) {
            log.warn("DTF trigger failed for order {}: {}", saved.getId(), e.getMessage());
        }
        return dto(saved);
    }

    public BigDecimal calculateVerifiedTotal(CreateOrderRequest r) {
        BigDecimal total = BigDecimal.ZERO;
        for (ItemRequest i : r.items()) {
            ProductVariant v = variants.findById(i.productVariantId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Variant not found"));
            if (v.getStockQuantity() < i.quantity()) throw new ApiException(HttpStatus.CONFLICT, "Insufficient stock");
            BigDecimal expected = v.getProduct().getBasePrice();
            boolean hasCustomDesign = (i.avatarId() != null)
                    || (i.printConfigJson() != null && !i.printConfigJson().isBlank() && !i.printConfigJson().trim().equals("{}"));
            if (v.getProduct().isCustomizable() && hasCustomDesign)
                expected = expected.add(v.getProduct().getPrintSurcharge());
            total = total.add(expected.multiply(BigDecimal.valueOf(i.quantity())));
        }
        return total;
    }

    public com.metacloth.api.dto.OrderDtos.TrackResponse track(Long uid, Long id) {
        var o = orders.findByIdAndUserId(id, uid).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found"));
        var timeline = new ArrayList<String>();
        timeline.add("PLACED");
        if (o.getOrderStatus() == OrderStatus.PLACED)
            return new com.metacloth.api.dto.OrderDtos.TrackResponse(o.getId(), o.getOrderNumber(), o.getOrderStatus().name(), timeline);
        timeline.add("IN_PRINTING");
        if (o.getOrderStatus() == OrderStatus.IN_PRINTING)
            return new com.metacloth.api.dto.OrderDtos.TrackResponse(o.getId(), o.getOrderNumber(), o.getOrderStatus().name(), timeline);
        timeline.add("SHIPPED");
        if (o.getOrderStatus() == OrderStatus.SHIPPED)
            return new com.metacloth.api.dto.OrderDtos.TrackResponse(o.getId(), o.getOrderNumber(), o.getOrderStatus().name(), timeline);
        timeline.add("DELIVERED");
        return new com.metacloth.api.dto.OrderDtos.TrackResponse(o.getId(), o.getOrderNumber(), o.getOrderStatus().name(), timeline);
    }

    public com.metacloth.api.dto.OrderDtos.TrackingDetailResponse trackPublic(String query) {
        if (query == null || query.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please provide an order number or phone number");
        }
        String clean = query.trim();
        Order o = orders.findByOrderNumberIgnoreCase(clean)
                .or(() -> orders.findFirstByShippingPhoneOrderByCreatedAtDesc(clean))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No order found for \"" + clean + "\""));

        OrderStatus st = o.getOrderStatus();
        String createdDateStr = o.getCreatedAt() != null ? o.getCreatedAt().toString() : "";

        List<com.metacloth.api.dto.OrderDtos.TrackingDetailResponse.TimelineStep> steps = new ArrayList<>();
        boolean placedDone = true;
        boolean inPrintDone = st == OrderStatus.IN_PRINTING || st == OrderStatus.SHIPPED || st == OrderStatus.DELIVERED;
        boolean shippedDone = st == OrderStatus.SHIPPED || st == OrderStatus.DELIVERED;
        boolean deliveredDone = st == OrderStatus.DELIVERED;

        steps.add(new com.metacloth.api.dto.OrderDtos.TrackingDetailResponse.TimelineStep(
                "PLACED",
                "Order Confirmed & Paid",
                "Payment verified via Razorpay · Queued for Tirupur Atelier production",
                createdDateStr,
                placedDone,
                st == OrderStatus.PLACED
        ));

        steps.add(new com.metacloth.api.dto.OrderDtos.TrackingDetailResponse.TimelineStep(
                "IN_PRINTING",
                "DTF Film Printing & Curing",
                "Ultra-HD 300 DPI hot-melt transfer print pressed onto 260+ GSM combed cotton blank",
                st == OrderStatus.IN_PRINTING ? "In progress at Tirupur Hub" : (inPrintDone ? "Completed" : "Scheduled"),
                inPrintDone,
                st == OrderStatus.IN_PRINTING
        ));

        steps.add(new com.metacloth.api.dto.OrderDtos.TrackingDetailResponse.TimelineStep(
                "SHIPPED",
                "Quality Inspected & Dispatched",
                "Packaged with branded tags and handed over to courier partner",
                shippedDone ? "Dispatched from Tirupur Hub" : "Pending dispatch",
                shippedDone,
                st == OrderStatus.SHIPPED
        ));

        steps.add(new com.metacloth.api.dto.OrderDtos.TrackingDetailResponse.TimelineStep(
                "DELIVERED",
                "Delivered to Destination",
                "Delivered to " + o.getCity() + ", " + o.getPostalCode(),
                deliveredDone ? "Delivered" : "Expected in 2-4 business days",
                deliveredDone,
                st == OrderStatus.DELIVERED
        ));

        String courier = "Delhivery Air Express";
        String awb = o.getRazorpayOrderId() != null ? "AWB" + o.getRazorpayOrderId().replaceAll("[^0-9A-Za-z]", "").substring(0, Math.min(10, o.getRazorpayOrderId().length())) : "AWB" + o.getId() + "TRP";
        String trackUrl = "https://www.delhivery.com/track/package/" + awb;

        var items = o.getItems().stream().map(i -> new com.metacloth.api.dto.OrderDtos.ItemResponse(
                i.getId(),
                i.getProductVariant().getId(),
                i.getAvatar() == null ? null : i.getAvatar().getId(),
                i.getQuantity(),
                i.getUnitPrice(),
                i.getPrintConfigJson(),
                i.getPreviewRenderUrl()
        )).toList();

        return new com.metacloth.api.dto.OrderDtos.TrackingDetailResponse(
                o.getId(),
                o.getOrderNumber(),
                st.name(),
                o.getShippingName(),
                o.getCity(),
                o.getPostalCode(),
                o.getTotalAmount(),
                createdDateStr,
                courier,
                awb,
                trackUrl,
                steps,
                items
        );
    }

    public List<OrderResponse> mine(Long uid) {
        return orders.findByUserIdOrderByCreatedAtDesc(uid).stream().map(this::dto).toList();
    }

    public List<OrderResponse> all() {
        return orders.findAllByOrderByCreatedAtDesc().stream().map(this::dto).toList();
    }

    @Transactional
    public OrderResponse status(Long id, String s) {
        Order o = orders.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Order not found"));
        try {
            o.setOrderStatus(OrderStatus.valueOf(s));
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid order status");
        }
        return dto(orders.save(o));
    }

    public OrderResponse dto(Order o) {
        return new OrderResponse(o.getId(), o.getOrderNumber(), o.getTotalAmount(), o.getOrderStatus().name(), o.getShippingName(), o.getShippingPhone(), o.getShippingAddressLine(), o.getCity(), o.getPostalCode(), o.getItems().stream().map(i -> new ItemResponse(i.getId(), i.getProductVariant().getId(), i.getAvatar() == null ? null : i.getAvatar().getId(), i.getQuantity(), i.getUnitPrice(), i.getPrintConfigJson(), i.getPreviewRenderUrl())).toList());
    }
}
