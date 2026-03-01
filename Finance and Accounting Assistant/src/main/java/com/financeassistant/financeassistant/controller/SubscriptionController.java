package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * PATH: finance-backend/src/main/java/com/financeassistant/financeassistant/controller/SubscriptionController.java
 *
 * NEW FILE — handles subscription management endpoints:
 *  POST /api/v1/subscription/start-trial  — explicitly start the 5-day trial
 *  GET  /api/v1/subscription/status       — get current tier + AI chat remaining
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    /**
     * POST /api/v1/subscription/start-trial
     * Starts the 5-day free trial for the current user.
     * Can only be called once per user account.
     */
    @PostMapping("/start-trial")
    public ResponseEntity<?> startTrial(@AuthenticationPrincipal User user) {
        boolean started = subscriptionService.startTrial(user);
        if (!started) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "TRIAL_ALREADY_USED",
                    "message", "Your free trial has already been used. Please upgrade to Pro."
            ));
        }
        return ResponseEntity.ok(Map.of(
                "message",          "Your 5-day free trial has started!",
                "tier",             "TRIAL",
                "trialDaysRemaining", 5,
                "aiChatsRemaining", user.getAiChatDailyLimit()
        ));
    }

    /**
     * GET /api/v1/subscription/status
     * Returns current subscription status for the authenticated user.
     */
    @GetMapping("/status")
    public ResponseEntity<?> getStatus(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of(
                "tier",             user.getEffectiveTier(),
                "status",           user.getSubscriptionStatus().name(),
                "trialDaysRemaining", user.trialDaysRemaining(),
                "aiChatsRemaining", user.getAiChatsRemainingToday(),
                "aiChatDailyLimit", user.getAiChatDailyLimit(),
                "hasPremiumAccess", user.hasPremiumAccess(),
                "trialAlreadyUsed", user.getTrialStartedAt() != null
        ));
    }
}