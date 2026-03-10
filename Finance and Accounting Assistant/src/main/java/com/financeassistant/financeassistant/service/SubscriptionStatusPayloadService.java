package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SubscriptionStatusPayloadService {

    private final BillingConfigurationService billingConfigurationService;

    public Map<String, Object> build(User user, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (message != null && !message.isBlank()) {
            payload.put("message", message);
        }
        payload.put("tier", user.getEffectiveTier());
        payload.put("status", user.getSubscriptionStatus().name());
        payload.put("trialDaysRemaining", user.trialDaysRemaining());
        payload.put("aiChatsRemaining", user.getAiChatsRemainingToday());
        payload.put("aiChatDailyLimit", user.getAiChatDailyLimit());
        payload.put("hasPremiumAccess", user.hasPremiumAccess());
        payload.put("trialAlreadyUsed", user.getTrialStartedAt() != null);
        payload.put("expiresAt", user.getSubscriptionExpiresAt() != null ? user.getSubscriptionExpiresAt().toString() : "");
        payload.put("paymentConfigured", billingConfigurationService.isPaymentConfigured());
        if (!billingConfigurationService.isPaymentConfigured()) {
            payload.put("paymentMessage", billingConfigurationService.getUnavailableMessage());
        }
        return payload;
    }
}
