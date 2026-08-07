package com.metacloth.api.controller;

import com.metacloth.api.dto.AuthDtos;
import com.metacloth.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {
    private final UserService users;

    @GetMapping
    Object me(Authentication a) {
        return users.dto(users.customer((Long) a.getPrincipal()));
    }

    @PutMapping("/profile")
    Object updateProfile(
            Authentication a,
            @Valid @RequestBody AuthDtos.UpdateProfileRequest request
    ) {
        return users.dto(
                users.updateProfile(
                        (Long) a.getPrincipal(),
                        request
                )
        );
    }
}
