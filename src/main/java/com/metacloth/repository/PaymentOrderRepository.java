package com.metacloth.repository;

import com.metacloth.domain.entity.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {
    Optional<PaymentOrder> findByRazorpayOrderId(String razorpayOrderId);

    Optional<PaymentOrder> findByRazorpayOrderIdAndUserId(String razorpayOrderId, Long userId);
}
