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
        boolean started = subscriptionService.startTrial(user);
        if (!started) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "TRIAL_ALREADY_USED",
                    "message", "Your free trial has already been used. Please upgrade to Pro."));
        }
        return ResponseEntity.ok(subscriptionStatusPayloadService.build(user, "Your 3-day free trial has started!"));
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(subscriptionStatusPayloadService.build(user, null));
    }

    @PostMapping("/cancel")
    public ResponseEntity<?> cancelSubscription(@AuthenticationPrincipal User user) {
        if (user.getSubscriptionStatus() == User.SubscriptionStatus.FREE) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "NO_ACTIVE_SUBSCRIPTION",
                    "message", "You don't have an active subscription to cancel."));
        }
        subscriptionService.cancelSubscription(user.getEmail());
        return ResponseEntity.ok(subscriptionStatusPayloadService.build(
                user,
                "Your subscription has been cancelled. You've been moved to the Free tier."));
    }
}
