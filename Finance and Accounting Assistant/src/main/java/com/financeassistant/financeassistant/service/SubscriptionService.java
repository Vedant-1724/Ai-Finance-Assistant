package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.entity.User.SubscriptionStatus;
import com.financeassistant.financeassistant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    public static final int TRIAL_DAYS = 3;

    private final UserRepository userRepository;
    private final WorkspaceAccessService workspaceAccessService;

    public enum TrialStartResult {
        STARTED,
        ALREADY_USED,
        FREE_TIER_ONLY,
        OWNER_ONLY
    }

    @Transactional
    public TrialStartResult startTrial(User user) {
        if (!workspaceAccessService.isWorkspaceOwner(user)) {
            log.warn("User {} attempted to start trial without owner access", user.getEmail());
            return TrialStartResult.OWNER_ONLY;
        }

        User billingUser = getBillingUser(user);
        String workspaceTier = billingUser.getEffectiveTier();
        if (!"FREE".equals(workspaceTier)) {
            log.warn("User {} attempted to start trial but workspace is on {}", user.getEmail(), workspaceTier);
            return TrialStartResult.FREE_TIER_ONLY;
        }

        if (billingUser.getTrialStartedAt() != null) {
            log.warn("User {} attempted to start trial but already used it", user.getEmail());
            return TrialStartResult.ALREADY_USED;
        }

        billingUser.setTrialStartedAt(Instant.now());
        billingUser.setSubscriptionStatus(SubscriptionStatus.TRIAL);
        userRepository.save(billingUser);
        log.info("Trial started for user {}", user.getEmail());
        return TrialStartResult.STARTED;
    }

    public boolean isTrialEligible(User user) {
        if (user == null || !workspaceAccessService.isWorkspaceOwner(user)) {
            return false;
        }

        User billingUser = getBillingUser(user);
        return "FREE".equals(billingUser.getEffectiveTier())
                && billingUser.getTrialStartedAt() == null;
    }

    public boolean hasPremiumAccess(User user) {
        return !"FREE".equals(getWorkspaceTier(user));
    }

    public long trialDaysRemaining(User user) {
        return getBillingUser(user).trialDaysRemaining();
    }

    public String getWorkspaceTier(User user) {
        return getBillingUser(user).getEffectiveTier();
    }

    public int getAiChatDailyLimit(User user) {
        return limitForTier(getWorkspaceTier(user));
    }

    @Transactional
    public int consumeAiChatMessage(User user) {
        LocalDate today = LocalDate.now();

        if (user.getAiChatResetDate() == null || !user.getAiChatResetDate().equals(today)) {
            user.setAiChatsUsedToday(0);
            user.setAiChatResetDate(today);
        }

        int limit = getAiChatDailyLimit(user);
        if (user.getAiChatsUsedToday() >= limit || limit <= 0) {
            log.warn("AI chat limit exceeded for user {} (tier: {})", user.getEmail(), getWorkspaceTier(user));
            return -1;
        }

        user.setAiChatsUsedToday(user.getAiChatsUsedToday() + 1);
        userRepository.save(user);
        return limit - user.getAiChatsUsedToday();
    }

    public int getAiChatsRemaining(User user) {
        LocalDate today = LocalDate.now();
        int limit = getAiChatDailyLimit(user);
        if (user.getAiChatResetDate() == null || !user.getAiChatResetDate().equals(today)) {
            return limit;
        }
        return Math.max(0, limit - user.getAiChatsUsedToday());
    }

    @Transactional
    public void activateSubscription(String email, String razorpayPaymentId, String plan) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
        Instant expiry = Instant.now().plus(30, ChronoUnit.DAYS);

        SubscriptionStatus finalStatus = "MAX".equalsIgnoreCase(plan) ? SubscriptionStatus.MAX
                : SubscriptionStatus.ACTIVE;
        user.setSubscriptionStatus(finalStatus);
        user.setSubscriptionExpiresAt(expiry);
        user.setRazorpaySubscriptionId(razorpayPaymentId);
        userRepository.save(user);
        log.info("Subscription {} ACTIVATED for {} - expires {}", finalStatus, email, expiry);
    }

    @Transactional
    public void renewSubscription(String email, String razorpayPaymentId, String plan) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
        Instant base = (user.getSubscriptionExpiresAt() != null && user.getSubscriptionExpiresAt().isAfter(Instant.now()))
                ? user.getSubscriptionExpiresAt()
                : Instant.now();
        Instant newExpiry = base.plus(30, ChronoUnit.DAYS);

        SubscriptionStatus finalStatus = "MAX".equalsIgnoreCase(plan) ? SubscriptionStatus.MAX
                : SubscriptionStatus.ACTIVE;
        user.setSubscriptionStatus(finalStatus);
        user.setSubscriptionExpiresAt(newExpiry);
        user.setRazorpaySubscriptionId(razorpayPaymentId);
        userRepository.save(user);
        log.info("Subscription {} RENEWED for {} - new expiry {}", finalStatus, email, newExpiry);
    }

    @Transactional
    public void expireTrialIfEnded(User user) {
        User billingUser = getBillingUser(user);
        if (billingUser.getSubscriptionStatus() == SubscriptionStatus.TRIAL && !billingUser.hasPremiumAccess()) {
            billingUser.setSubscriptionStatus(SubscriptionStatus.EXPIRED);
            userRepository.save(billingUser);
            log.info("Trial EXPIRED for {}", billingUser.getEmail());
        }
    }

    private User getBillingUser(User user) {
        return workspaceAccessService.getRequiredWorkspace(user).workspaceOwner();
    }

    private int limitForTier(String tier) {
        return switch (tier) {
            case "MAX" -> 50;
            case "ACTIVE" -> 20;
            default -> 0;
        };
    }
}
