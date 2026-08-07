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
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orders;
    private final UserService users;
    private final ProductVariantRepository variants;
    private final AvatarRepository avatars;

    @Transactional
    public OrderResponse create(Long uid, CreateOrderRequest r) {
        User u = users.customer(uid);
        Order o = Order.builder().orderNumber("MC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase()).user(u).shippingName(r.shippingName()).shippingPhone(r.shippingPhone()).shippingAddressLine(r.shippingAddressLine()).city(r.city()).postalCode(r.postalCode()).orderStatus(OrderStatus.PLACED).totalAmount(BigDecimal.ZERO).build();
        BigDecimal total = BigDecimal.ZERO;
        for (ItemRequest i : r.items()) {
            ProductVariant v = variants.findByIdForUpdate(i.productVariantId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Variant not found"));
            if (v.getStockQuantity() < i.quantity()) throw new ApiException(HttpStatus.CONFLICT, "Insufficient stock");
            BigDecimal expected = v.getProduct().getBasePrice();
            if (v.getProduct().isCustomizable() && i.avatarId() != null)
                expected = expected.add(v.getProduct().getPrintSurcharge());
            if (i.unitPrice().compareTo(expected) != 0)
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid unit price");
            v.setStockQuantity(v.getStockQuantity() - i.quantity());
            Avatar a = i.avatarId() == null ? null : avatars.findById(i.avatarId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Avatar not found"));
            o.getItems().add(OrderItem.builder().order(o).productVariant(v).avatar(a).quantity(i.quantity()).unitPrice(expected).printConfigJson(i.printConfigJson()).previewRenderUrl(i.previewRenderUrl()).build());
            total = total.add(expected.multiply(BigDecimal.valueOf(i.quantity())));
        }
        o.setTotalAmount(total);
        return dto(orders.save(o));
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
