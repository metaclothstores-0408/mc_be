package com.metacloth.api.controller;

import com.metacloth.api.dto.AuthDtos.*;
import com.metacloth.service.AuthService;
import com.metacloth.service.OtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;
    private final OtpService otp;

    @PostMapping("/signup")
    AuthResponse signup(@Valid @RequestBody SignupRequest r) {
        return auth.signup(r);
    }

    @PostMapping("/signin")
    AuthResponse signin(@Valid @RequestBody SigninRequest r) {
        return auth.signin(r);
    }

    @PostMapping("/otp/send")
    OtpSendResponse send(@Valid @RequestBody OtpSendRequest r) {
        return otp.send(r.phoneNumber());
    }

    @PostMapping("/otp/verify")
    AuthResponse verify(@Valid @RequestBody OtpVerifyRequest r) {
        return auth.otp(r);
    }

    @PostMapping("/google")
    AuthResponse google(@Valid @RequestBody GoogleLoginRequest r) {
        return auth.google(r.idToken());
    }
}
