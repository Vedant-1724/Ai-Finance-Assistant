package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.dto.CategoryOptionDto;
import com.financeassistant.financeassistant.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/{companyId}/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<List<CategoryOptionDto>> getCategories(@PathVariable Long companyId) {
        return ResponseEntity.ok(categoryService.getCategories(companyId));
    }
}
