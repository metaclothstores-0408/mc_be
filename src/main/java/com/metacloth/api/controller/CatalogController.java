package com.metacloth.api.controller;

import com.metacloth.service.CatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CatalogController {
    private final CatalogService service;

    @GetMapping("/avatars")
    Object avatars() {
        return service.avatars();
    }

    @GetMapping("/products/customizable")
    Object products() {
        return service.products();
    }

    @GetMapping("/products/{id}")
    public Object product(@PathVariable Long id) {
        return service.product(id);
    }
}
