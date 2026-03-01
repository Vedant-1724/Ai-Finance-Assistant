package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final SubscriptionService subscriptionService;
    private final RestTemplate        restTemplate;

    @Value("${ai.service.url:http://localhost:5000}")
    private String aiServiceUrl;

    /**
     * POST /api/v1/ai/chat
     * Proxies to Python Flask /chat with per-user daily limit enforcement.
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/chat")
    public ResponseEntity<?> chat(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, Object> body) {

        // Check and consume daily limit
        int remaining = subscriptionService.consumeAiChatMessage(user);
        if (remaining == -1) {
            int limit = user.getAiChatDailyLimit();
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "error",      "DAILY_LIMIT_EXCEEDED",
                    "message",    "You've used all " + limit + " AI chats for today. Resets at midnight.",
                    "tier",       user.getEffectiveTier(),
                    "dailyLimit", limit,
                    "upgradeUrl", "/subscription"
            ));
        }

        // Forward to Python AI service
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // ✅ FIX: Use explicit type to avoid unchecked warning
            ResponseEntity<Map> aiResponse = restTemplate.exchange(
                    aiServiceUrl + "/chat",
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );

            Map<String, Object> responseBody = new HashMap<>();
            if (aiResponse.getBody() != null) {
                responseBody.putAll(aiResponse.getBody());
            }
            responseBody.put("aiChatsRemaining", remaining);
            responseBody.put("aiChatDailyLimit", user.getAiChatDailyLimit());

            return ResponseEntity.ok(responseBody);

        } catch (Exception e) {
            log.error("AI service error for user {}: {}", user.getEmail(), e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "error",   "AI_SERVICE_UNAVAILABLE",
                    "message", "The AI assistant is temporarily unavailable. Please try again shortly."
            ));
        }
    }
}