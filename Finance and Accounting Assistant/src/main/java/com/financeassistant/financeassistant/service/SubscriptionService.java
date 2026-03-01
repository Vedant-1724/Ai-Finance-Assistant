package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.entity.User.SubscriptionStatus;
import com.financeassistant.financeassistant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * PATH: Finance and Accounting Assistant/src/main/java/com/financeassistant/
 *       financeassistant/service/SubscriptionService.java
 *
 * NEW FILE — handles all subscription lifecycle:
 *   - Trial start / days remaining
 *   - Activation after Razorpay payment confirmed (webhook)
 *   - Cancellation / expiry management
 *
 * Called by:
 *   PaymentController  — activate after webhook, get status
 *   SubscriptionFilter — canAccess() check on every API call
 *   AuthService        — startTrialIfNotStarted() on registration
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final int TRIAL_DAYS = 5;

    private final UserRepository userRepository;

    /**
     * Starts the free trial for a new user if it hasn't been started yet.
     * Called from AuthService.register() so every new user gets 5 days free.
     */
    @Transactional
    public void startTrialIfNotStarted(User user) {
        if (user.getTrialStartedAt() == null) {
            user.setTrialStartedAt(Instant.now());
            user.setSubscriptionStatus(SubscriptionStatus.TRIAL);
            userRepository.save(user);
            log.info("Trial started for user {}", user.getEmail());
        }
    }

    /**
     * Returns true if user can access the app right now.
     * Thin wrapper around User.isSubscriptionActive() for convenience.
     */
    public boolean canAccess(User user) {
        return user.isSubscriptionActive();
    }

    /**
     * Returns days remaining in the free trial (0 if expired or paid).
     */
    public long trialDaysRemaining(User user) {
        return user.trialDaysRemaining();
    }

    /**
     * Activates a paid subscription after a Razorpay payment.captured webhook.
     * Sets status to ACTIVE and extends expiry by 30 days from now.
     */
    @Transactional
    public void activateSubscription(String email, String razorpayPaymentId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));

        Instant expiry = Instant.now().plus(30, ChronoUnit.DAYS);
        user.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        user.setSubscriptionExpiresAt(expiry);
        user.setRazorpaySubscriptionId(razorpayPaymentId);
        userRepository.save(user);

        log.info("Subscription ACTIVATED for user {} — expires {}", email, expiry);
    }

    /**
     * Extends an existing subscription by 30 days (for renewals).
     */
    @Transactional
    public void renewSubscription(String email, String razorpayPaymentId) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));

        Instant base = (user.getSubscriptionExpiresAt() != null &&
                        user.getSubscriptionExpiresAt().isAfter(Instant.now()))
                       ? user.getSubscriptionExpiresAt()
                       : Instant.now();

        Instant newExpiry = base.plus(30, ChronoUnit.DAYS);
        user.setSubscriptionStatus(SubscriptionStatus.ACTIVE);
        user.setSubscriptionExpiresAt(newExpiry);
        user.setRazorpaySubscriptionId(razorpayPaymentId);
        userRepository.save(user);

        log.info("Subscription RENEWED for user {} — new expiry {}", email, newExpiry);
    }

    /**
     * Marks subscription as cancelled. User retains access until expiry date.
     */
    @Transactional
    public void cancelSubscription(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));

        user.setSubscriptionStatus(SubscriptionStatus.CANCELLED);
        userRepository.save(user);
        log.info("Subscription CANCELLED for user {}", email);
    }

    /**
     * Marks trial as expired. Called by a @Scheduled job (optional).
     */
    @Transactional
    public void expireTrialIfEnded(User user) {
        if (user.getSubscriptionStatus() == SubscriptionStatus.TRIAL
                && !user.isSubscriptionActive()) {
            user.setSubscriptionStatus(SubscriptionStatus.EXPIRED);
            userRepository.save(user);
            log.info("Trial EXPIRED for user {}", user.getEmail());
        }
    }
}
