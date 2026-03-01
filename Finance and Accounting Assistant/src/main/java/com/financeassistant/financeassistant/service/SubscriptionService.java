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

/**
 * PATH: finance-backend/src/main/java/com/financeassistant/financeassistant/service/SubscriptionService.java
 *
 * CHANGES:
 *  - startTrial() is now an explicit user action (not auto-called on register)
 *  - Added incrementAiChatUsage() and getAiChatsRemaining() for daily quota
 *  - FREE/EXPIRED/CANCELLED all map to free-tier behaviour (not hard-blocked)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final int TRIAL_DAYS = 5;

    private final UserRepository userRepository;

    // ── Trial management ──────────────────────────────────────────────────────

    /**
     * Explicitly starts the 5-day trial for a FREE/EXPIRED user.
     * Called from SubscriptionController POST /api/v1/subscription/start-trial.
     * Returns false if trial already used.
     */
    @Transactional
    public boolean startTrial(User user) {
        // Can only start trial once — if trialStartedAt is already set, deny
        if (user.getTrialStartedAt() != null) {
            log.warn("User {} attempted to start trial but already used it", user.getEmail());
            return false;
        }
        user.setTrialStartedAt(Instant.now());
        user.setSubscriptionStatus(SubscriptionStatus.TRIAL);
        userRepository.save(user);
        log.info("Trial started for user {}", user.getEmail());
        return true;
    }

    /**
     * Returns true if the user can access premium features.
     */
    public boolean hasPremiumAccess(User user) {
        return user.hasPremiumAccess();
    }

    /**
     * Returns days remaining in free trial.
     */
    public long trialDaysRemaining(User user) {
        return user.trialDaysRemaining();
    }

    // ── AI Chat daily quota ───────────────────────────────────────────────────

    /**
     * Attempts to consume one AI chat message for the given user.
     * Returns the number of chats remaining AFTER this message, or -1 if limit exceeded.
     */
    @Transactional
    public int consumeAiChatMessage(User user) {
        LocalDate today = LocalDate.now();

        // Reset counter if it's a new day
        if (user.getAiChatResetDate() == null || !user.getAiChatResetDate().equals(today)) {
            user.setAiChatsUsedToday(0);
            user.setAiChatResetDate(today);
        }

        int limit = user.getAiChatDailyLimit();
        if (user.getAiChatsUsedToday() >= limit) {
            log.warn("AI chat limit exceeded for user {} (tier: {})", user.getEmail(), user.getEffectiveTier());
            return -1; // Limit exceeded
        }

        user.setAiChatsUsedToday(user.getAiChatsUsedToday() + 1);
        userRepository.save(user);
        return limit - user.getAiChatsUsedToday(); // remaining after this message
    }

    /**
     * Returns remaining AI chats today without consuming one.
     */
    public int getAiChatsRemaining(User user) {
        return user.getAiChatsRemainingToday();
    }

    // ── Subscription activation / renewal / cancellation ─────────────────────

    @Transactional
    public void activateSubscription(String email, String razorpayPaymentId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
        Instant expiry = Instant.now().plus(30, ChronoUnit.DAYS);
        user.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        user.setSubscriptionExpiresAt(expiry);
        user.setRazorpaySubscriptionId(razorpayPaymentId);
        userRepository.save(user);
        log.info("Subscription ACTIVATED for {} — expires {}", email, expiry);
    }

    @Transactional
    public void renewSubscription(String email, String razorpayPaymentId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
        Instant base = (user.getSubscriptionExpiresAt() != null &&
                        user.getSubscriptionExpiresAt().isAfter(Instant.now()))
                       ? user.getSubscriptionExpiresAt() : Instant.now();
        Instant newExpiry = base.plus(30, ChronoUnit.DAYS);
        user.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        user.setSubscriptionExpiresAt(newExpiry);
        user.setRazorpaySubscriptionId(razorpayPaymentId);
        userRepository.save(user);
        log.info("Subscription RENEWED for {} — new expiry {}", email, newExpiry);
    }

    @Transactional
    public void cancelSubscription(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
        user.setSubscriptionStatus(SubscriptionStatus.CANCELLED);
        userRepository.save(user);
        log.info("Subscription CANCELLED for {}", email);
    }

    @Transactional
    public void expireTrialIfEnded(User user) {
        if (user.getSubscriptionStatus() == SubscriptionStatus.TRIAL
                && !user.hasPremiumAccess()) {
            user.setSubscriptionStatus(SubscriptionStatus.EXPIRED);
            userRepository.save(user);
            log.info("Trial EXPIRED for {}", user.getEmail());
        }
    }
}