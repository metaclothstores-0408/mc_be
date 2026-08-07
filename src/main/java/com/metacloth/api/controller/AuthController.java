package com.metacloth.api.controller;

import com.metacloth.api.dto.AuthDtos.*;
import com.metacloth.config.AppProperties;
import com.metacloth.repository.UserRepository;
import com.metacloth.security.JwtService;
import com.metacloth.service.AuthService;
import com.metacloth.service.GoogleOAuthService;
import com.metacloth.service.OtpService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Arrays;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String STATE_COOKIE =
            "MC_GOOGLE_OAUTH_STATE";

    private final AuthService auth;
    private final OtpService otp;
    private final GoogleOAuthService googleOAuth;
    private final JwtService jwt;
    private final UserRepository users;
    private final AppProperties properties;

    @PostMapping("/signup")
    AuthResponse signup(
            @Valid @RequestBody SignupRequest r
    ) {
        return auth.signup(r);
    }

    @PostMapping("/signin")
    AuthResponse signin(
            @Valid @RequestBody SigninRequest r
    ) {
        return auth.signin(r);
    }

    @PostMapping("/otp/send")
    OtpSendResponse send(
            @Valid @RequestBody OtpSendRequest r
    ) {
        return otp.send(r.phoneNumber());
    }

    @PostMapping("/otp/verify")
    AuthResponse verify(
            @Valid @RequestBody OtpVerifyRequest r
    ) {
        return auth.otp(r);
    }

    @GetMapping("/google/start")
    void googleStart(
            HttpServletResponse response
    ) throws IOException {

        response.sendRedirect(
                googleOAuth.startAuthorization(response)
        );
    }

    @GetMapping("/google/callback")
    void googleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        if (error != null) {
            response.sendRedirect(
                    properties.google().frontendCallbackUrl()
                            + "?error=google_auth_denied"
            );
            return;
        }

        String expectedState = null;

        if (request.getCookies() != null) {

            expectedState =
                    Arrays.stream(request.getCookies())
                            .filter(c ->
                                    STATE_COOKIE.equals(
                                            c.getName()
                                    )
                            )
                            .map(Cookie::getValue)
                            .findFirst()
                            .orElse(null);
        }

        try {

            googleOAuth.handleCallback(
                    code,
                    state,
                    expectedState,
                    response
            );

            response.sendRedirect(
                    properties.google()
                            .frontendCallbackUrl()
            );

        } catch (Exception ex) {

            response.sendRedirect(
                    properties.google()
                            .frontendCallbackUrl()
                            + "?error=google_auth_failed"
            );
        }
    }

    @GetMapping("/google/session")
    AuthResponse googleSession(
            HttpServletRequest request,
            HttpServletResponse response
    ) {

        String token =
                googleOAuth.consumeSessionCookie(
                        request,
                        response
                );

        if (!jwt.valid(token)) {

            throw new com.metacloth.exception.ApiException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid authentication session."
            );
        }

        Long userId = jwt.userId(token);

        var user =
                users.findById(userId)
                        .filter(u -> u.isEnabled())
                        .orElseThrow(() ->
                                new com.metacloth.exception.ApiException(
                                        HttpStatus.UNAUTHORIZED,
                                        "User account is unavailable."
                                )
                        );

        return new AuthResponse(
                token,
                "Bearer",
                jwt.expires(),
                new UserResponse(
                        user.getId(),
                        user.getFullName(),
                        user.getEmail(),
                        user.getPhoneNumber(),
                        user.getRole().name(),
                        user.getSelectedAvatar() == null
                                ? null
                                : user.getSelectedAvatar().getId()
                )
        );
    }

    /*
     * Existing ID-token endpoint.
     * Keep temporarily for backward compatibility.
     */
    @PostMapping("/google")
    AuthResponse google(
            @Valid @RequestBody GoogleLoginRequest r
    ) {
        return auth.google(r.idToken());
    }
}