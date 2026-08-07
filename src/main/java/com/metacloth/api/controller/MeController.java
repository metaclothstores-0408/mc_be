package com.metacloth.api.controller;

import com.metacloth.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {
    private final UserService users;

    @GetMapping
    Object me(Authentication a) {
        return users.dto(users.customer((Long) a.getPrincipal()));
    }
}
