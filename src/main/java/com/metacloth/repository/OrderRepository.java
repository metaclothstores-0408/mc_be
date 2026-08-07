package com.metacloth.repository;

import com.metacloth.domain.entity.Order;
import com.metacloth.domain.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Order> findByIdAndUserId(Long id, Long userId);

    List<Order> findAllByOrderByCreatedAtDesc();

    long countByOrderStatus(OrderStatus status);
}
