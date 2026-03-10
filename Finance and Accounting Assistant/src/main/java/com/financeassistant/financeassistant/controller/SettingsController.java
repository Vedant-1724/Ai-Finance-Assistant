package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.dto.UserSettingsDto;
import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    public ResponseEntity<UserSettingsDto> getSettings(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(settingsService.getSettings(user));
    }

    @PostMapping
    public ResponseEntity<UserSettingsDto> updateSettings(
            @AuthenticationPrincipal User user,
            @RequestBody UserSettingsDto request) {
        return ResponseEntity.ok(settingsService.updateSettings(user, request));
    }
}
