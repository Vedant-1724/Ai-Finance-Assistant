package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.service.SubscriptionService;
import com.financeassistant.financeassistant.service.SubscriptionStatusPayloadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final SubscriptionStatusPayloadService subscriptionStatusPayloadService;

    @PostMapping("/start-trial")
    public ResponseEntity<?> startTrial(@AuthenticationPrincipal User user) {
        SubscriptionService.TrialStartResult result = subscriptionService.startTrial(user);
        return switch (result) {
            case STARTED -> ResponseEntity.ok(
                    subscriptionStatusPayloadService.build(user, "Your 3-day free trial has started!"));
            case OWNER_ONLY -> ResponseEntity.status(403).body(Map.of(
                    "error", "OWNER_ONLY",
                    "message", "Only the workspace owner can start a free trial."));
            case ALREADY_USED -> ResponseEntity.badRequest().body(Map.of(
                    "error", "TRIAL_ALREADY_USED",
                    "message", "Your free trial has already been used. Please choose Pro or Max."));
            case FREE_TIER_ONLY -> ResponseEntity.badRequest().body(Map.of(
                    "error", "TRIAL_FREE_TIER_ONLY",
                    "message", "Free trial is only available to users on the Free tier."));
        };
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(subscriptionStatusPayloadService.build(user, null));
    }
}
