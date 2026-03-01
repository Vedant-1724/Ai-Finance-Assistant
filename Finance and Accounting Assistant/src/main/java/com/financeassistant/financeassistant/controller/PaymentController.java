package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.service.SubscriptionService;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * PATH: Finance and Accounting Assistant/src/main/java/com/financeassistant/
 *       financeassistant/controller/PaymentController.java
 *
 * NEW FILE — Razorpay payment integration.
 *
 * Flow:
 *  1. React calls POST /create-order  → gets orderId + keyId
 *  2. React opens Razorpay checkout   → user pays ₹499
 *  3. Razorpay calls POST /webhook    → we verify signature + activate subscription
 *  4. React calls GET  /status        → shows trial/active/expired state in UI
 *
 * Security:
 *  - /webhook is public but ALWAYS verifies HMAC-SHA256 signature
 *  - /create-order and /status require valid JWT
 *  - Razorpay key-id and key-secret are env-var only (never in source)
 *
 * Required dependency in pom.xml:
 *   <groupId>com.razorpay</groupId>
 *   <artifactId>razorpay-java</artifactId>
 *   <version>1.4.5</version>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

    @Value("${razorpay.key.id}")
    private String keyId;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    @Value("${razorpay.webhook.secret}")
    private String webhookSecret;

    private final SubscriptionService subscriptionService;

    // ── 1. Create Razorpay order ─────────────────────────────────────────────

    /**
     * POST /api/v1/payment/create-order
     * Called by React Subscription page before opening Razorpay checkout.
     * Returns orderId, amount (paise), currency, and keyId for the JS SDK.
     */
    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(
            @AuthenticationPrincipal User user) {
        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);

            JSONObject opts = new JSONObject();
            opts.put("amount",   49900);          // ₹499.00 in paise (1 rupee = 100 paise)
            opts.put("currency", "INR");
            opts.put("receipt",  "rcpt_" + System.currentTimeMillis());
            opts.put("notes",    new JSONObject().put("email", user.getEmail()));

            Order order = client.orders.create(opts);
            log.info("Razorpay order created: {} for user {}", order.get("id"), user.getEmail());

            return ResponseEntity.ok(Map.of(
                "orderId",  order.get("id"),
                "amount",   order.get("amount"),
                "currency", order.get("currency"),
                "keyId",    keyId,
                "email",    user.getEmail()
            ));

        } catch (Exception e) {
            log.error("Failed to create Razorpay order for {}: {}", user.getEmail(), e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Payment order creation failed. Please try again."));
        }
    }

    // ── 2. Webhook — Razorpay calls this after payment ───────────────────────

    /**
     * POST /api/v1/payment/webhook
     * PUBLIC endpoint — Razorpay calls this with payment events.
     * CRITICAL: Always verify X-Razorpay-Signature before trusting the payload.
     *
     * Register this URL in your Razorpay Dashboard:
     *   Settings → Webhooks → Add New Webhook
     *   URL: https://yourdomain.com/api/v1/payment/webhook
     *   Events: payment.captured, subscription.activated
     */
    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        // 1. Reject requests without a signature immediately
        if (signature == null || signature.isBlank()) {
            log.warn("Webhook received without signature — rejected");
            return ResponseEntity.badRequest().build();
        }

        // 2. Verify HMAC-SHA256 signature using webhook secret
        try {
            boolean valid = Utils.verifyWebhookSignature(payload, signature, webhookSecret);
            if (!valid) {
                log.warn("Invalid webhook signature — rejected");
                return ResponseEntity.badRequest().build();
            }
        } catch (Exception e) {
            log.error("Webhook signature verification error: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }

        // 3. Process the verified event
        try {
            JSONObject json  = new JSONObject(payload);
            String    event  = json.getString("event");
            log.info("Razorpay webhook event: {}", event);

            if ("payment.captured".equals(event)) {
                JSONObject paymentEntity = json
                        .getJSONObject("payload")
                        .getJSONObject("payment")
                        .getJSONObject("entity");

                String email     = paymentEntity.getJSONObject("notes").optString("email", "");
                String paymentId = paymentEntity.getString("id");

                if (!email.isBlank()) {
                    subscriptionService.activateSubscription(email, paymentId);
                    log.info("Subscription activated via webhook for email={}", email);
                } else {
                    log.warn("Webhook payment.captured missing email in notes — skipped");
                }
            }

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            // Return 200 to prevent Razorpay retrying — log the error
            log.error("Error processing webhook payload: {}", e.getMessage());
            return ResponseEntity.ok().build();
        }
    }

    // ── 3. Subscription status ───────────────────────────────────────────────

    /**
     * GET /api/v1/payment/status
     * Called by React TrialBanner on every page load.
     * Returns current subscription state and trial countdown.
     */
    @GetMapping("/status")
    public ResponseEntity<?> getStatus(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of(
                "status",           user.getSubscriptionStatus().name(),
                "tier",             user.getEffectiveTier(),
                "trialDaysRemaining", subscriptionService.trialDaysRemaining(user),
                "hasPremiumAccess", subscriptionService.hasPremiumAccess(user),
                "expiresAt",        user.getSubscriptionExpiresAt() != null
                        ? user.getSubscriptionExpiresAt().toString() : ""
        ));
    }
}
