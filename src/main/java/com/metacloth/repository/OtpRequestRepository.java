package com.metacloth.repository;

import com.metacloth.domain.entity.OtpRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpRequestRepository extends JpaRepository<OtpRequest, Long> {
    Optional<OtpRequest> findFirstByPhoneNumberAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(String phoneNumber, String purpose);
}
