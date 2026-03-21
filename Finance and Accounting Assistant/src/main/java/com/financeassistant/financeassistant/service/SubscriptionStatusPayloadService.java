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
    private final SubscriptionService subscriptionService;
    private final WorkspaceAccessService workspaceAccessService;

    public Map<String, Object> build(User user, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        WorkspaceAccessService.WorkspaceContext workspace = workspaceAccessService.getRequiredWorkspace(user);
        User billingUser = workspace.workspaceOwner();
        String tier = subscriptionService.getWorkspaceTier(user);
        boolean trialAlreadyUsed = billingUser.getTrialStartedAt() != null;
        boolean trialEligible = subscriptionService.isTrialEligible(user);

        if (message != null && !message.isBlank()) {
            payload.put("message", message);
        }
        payload.put("companyId", workspace.companyId());
        payload.put("role", workspace.role().name());
        payload.put("tier", tier);
        payload.put("status", billingUser.getSubscriptionStatus().name());
        payload.put("trialDaysRemaining", subscriptionService.trialDaysRemaining(user));
        payload.put("aiChatsRemaining", subscriptionService.getAiChatsRemaining(user));
        payload.put("aiChatDailyLimit", subscriptionService.getAiChatDailyLimit(user));
        payload.put("hasPremiumAccess", subscriptionService.hasPremiumAccess(user));
        payload.put("trialAlreadyUsed", trialAlreadyUsed);
        payload.put("trialEligible", trialEligible);
        payload.put("canManageBilling", workspace.isOwner());
        payload.put("expiresAt", billingUser.getSubscriptionExpiresAt() != null ? billingUser.getSubscriptionExpiresAt().toString() : "");
        payload.put("paymentConfigured", billingConfigurationService.isPaymentConfigured());
        if (!billingConfigurationService.isPaymentConfigured()) {
            payload.put("paymentMessage", billingConfigurationService.getUnavailableMessage());
        }
        return payload;
    }
}
