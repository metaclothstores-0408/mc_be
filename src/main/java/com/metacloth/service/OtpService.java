package com.metacloth.service;

import com.metacloth.api.dto.AuthDtos.OtpSendResponse;
import com.metacloth.config.AppProperties;
import com.metacloth.domain.entity.OtpRequest;
import com.metacloth.exception.ApiException;
import com.metacloth.repository.OtpRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class OtpService {
    private final OtpRequestRepository repo;
    private final PasswordEncoder encoder;
    private final OtpSender sender;
    private final AppProperties p;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public OtpSendResponse send(String raw) {
        String phone = normalize(raw);
        OffsetDateTime now = OffsetDateTime.now();
        var last = repo.findFirstByPhoneNumberAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(phone, "LOGIN");
        if (last.isPresent() && last.get().getCreatedAt().plusSeconds(p.otp().resendCooldownSeconds()).isAfter(now))
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Please wait before requesting another OTP.");
        String otp = String.format("%0" + p.otp().length() + "d", random.nextInt((int) Math.pow(10, p.otp().length())));
        var r = repo.save(OtpRequest.builder().phoneNumber(phone).otpHash(encoder.encode(otp)).purpose("LOGIN").expiresAt(now.plusMinutes(p.otp().expiryMinutes())).attemptCount(0).build());
        sender.send(phone, otp);
        return new OtpSendResponse("OTP sent successfully.", r.getExpiresAt());
    }

    @Transactional
    public void verify(String raw, String otp) {
        String phone = normalize(raw);
        var r = repo.findFirstByPhoneNumberAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(phone, "LOGIN").orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "OTP not found."));
        if (r.getExpiresAt().isBefore(OffsetDateTime.now()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "OTP expired.");
        if (r.getAttemptCount() >= p.otp().maxAttempts())
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "Maximum OTP attempts exceeded.");
        r.setAttemptCount(r.getAttemptCount() + 1);
        if (!encoder.matches(otp, r.getOtpHash())) {
            repo.save(r);
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid OTP.");
        }
        r.setConsumedAt(OffsetDateTime.now());
        repo.save(r);
    }

    public String normalize(String phone) {
        return phone.trim().replaceAll("[\\s-]", "");
    }
}
