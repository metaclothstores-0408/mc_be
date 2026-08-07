package com.metacloth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public final class AuthDtos {
    public record SignupRequest(@NotBlank String fullName, @Email @NotBlank String email,
                                @Size(min = 8, max = 100) String password) {
    }

    public record SigninRequest(@Email @NotBlank String email, @NotBlank String password) {
    }

    public record OtpSendRequest(@NotBlank @Pattern(regexp = "^\\+?[1-9]\\d{9,14}$") String phoneNumber) {
    }

    public record OtpVerifyRequest(@NotBlank String phoneNumber, @NotBlank @Pattern(regexp = "^\\d{6}$") String otp) {
    }

    public record GoogleLoginRequest(@NotBlank String idToken) {
    }

    public record UserResponse(Long id, String fullName, String email, String phoneNumber, String role,
                               Long selectedAvatarId) {
    }

    public record AuthResponse(String accessToken, String tokenType, long expiresInSeconds, UserResponse user) {
    }

    public record OtpSendResponse(String message, OffsetDateTime expiresAt) {
    }
}
