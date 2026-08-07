package com.metacloth.api.controller;

import com.metacloth.api.dto.AuthDtos.AuthResponse;
import com.metacloth.api.dto.AuthDtos.SigninRequest;
import com.metacloth.domain.enums.UserRole;
import com.metacloth.exception.ApiException;
import com.metacloth.repository.UserRepository;
import com.metacloth.security.JwtService;
import com.metacloth.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/admin")
@RequiredArgsConstructor
public class AdminAuthController {
    private final UserRepository repo;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final UserService users;

    @PostMapping("/signin")
    AuthResponse signin(@Valid @RequestBody SigninRequest r) {
        var u = repo.findByEmailIgnoreCase(r.email()).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid admin credentials."));
        if (u.getRole() != UserRole.ADMIN || !encoder.matches(r.password(), u.getPasswordHash()))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid admin credentials.");
        return new AuthResponse(jwt.generate(u), "Bearer", jwt.expires(), users.dto(u));
    }
}
