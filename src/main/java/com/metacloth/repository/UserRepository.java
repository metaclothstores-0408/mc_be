package com.metacloth.repository;

import com.metacloth.domain.entity.User;
import com.metacloth.domain.enums.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByPhoneNumber(String phone);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhoneNumberAndIdNot(String phoneNumber, Long id);

    List<User> findAllByRoleOrderByCreatedAtDesc(UserRole role);
}
