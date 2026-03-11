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

    public enum TrialStartResult {
        STARTED,
        ALREADY_USED,
        FREE_TIER_ONLY
    }

    @Transactional
    public TrialStartResult startTrial(User user) {
        if (!"FREE".equals(user.getEffectiveTier())) {
            log.warn("User {} attempted to start trial but is on {}", user.getEmail(), user.getEffectiveTier());
            return TrialStartResult.FREE_TIER_ONLY;
        }

        if (user.getTrialStartedAt() != null) {
            log.warn("User {} attempted to start trial but already used it", user.getEmail());
            return TrialStartResult.ALREADY_USED;
        }

        user.setTrialStartedAt(Instant.now());
        user.setSubscriptionStatus(SubscriptionStatus.TRIAL);
        userRepository.save(user);
        log.info("Trial started for user {}", user.getEmail());
        return TrialStartResult.STARTED;
    }

    public boolean isTrialEligible(User user) {
        return user != null
                && "FREE".equals(user.getEffectiveTier())
                && user.getTrialStartedAt() == null;
    }

    public boolean hasPremiumAccess(User user) {
        return user.hasPremiumAccess();
    }

    public long trialDaysRemaining(User user) {
        return user.trialDaysRemaining();
    }

    @Transactional
    public int consumeAiChatMessage(User user) {
        LocalDate today = LocalDate.now();

        if (user.getAiChatResetDate() == null || !user.getAiChatResetDate().equals(today)) {
            user.setAiChatsUsedToday(0);
            user.setAiChatResetDate(today);
        }

        int limit = user.getAiChatDailyLimit();
        if (user.getAiChatsUsedToday() >= limit || limit <= 0) {
            log.warn("AI chat limit exceeded for user {} (tier: {})", user.getEmail(), user.getEffectiveTier());
            return -1;
        }

        user.setAiChatsUsedToday(user.getAiChatsUsedToday() + 1);
        userRepository.save(user);
        return limit - user.getAiChatsUsedToday();
    }

    public int getAiChatsRemaining(User user) {
        return user.getAiChatsRemainingToday();
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
        if (user.getSubscriptionStatus() == SubscriptionStatus.TRIAL && !user.hasPremiumAccess()) {
            user.setSubscriptionStatus(SubscriptionStatus.EXPIRED);
            userRepository.save(user);
            log.info("Trial EXPIRED for {}", user.getEmail());
        }
    }
}
