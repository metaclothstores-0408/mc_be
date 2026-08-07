package com.metacloth.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "otp_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(name = "phone_number")
    String phoneNumber;
    @Column(name = "otp_hash")
    String otpHash;
    String purpose;
    @Column(name = "expires_at")
    OffsetDateTime expiresAt;
    @Column(name = "consumed_at")
    OffsetDateTime consumedAt;
    @Column(name = "attempt_count")
    int attemptCount;
    @Column(name = "created_at")
    OffsetDateTime createdAt;

    @PrePersist
    void p() {
        createdAt = OffsetDateTime.now();
    }
}
