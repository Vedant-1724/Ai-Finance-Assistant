package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.service.BillingConfigurationService;
import com.financeassistant.financeassistant.service.SubscriptionService;
import com.financeassistant.financeassistant.service.SubscriptionStatusPayloadService;
import com.financeassistant.financeassistant.service.WorkspaceAccessService;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

    private static final String EVENT_PREFIX = "razorpay:event:";

    private final SubscriptionService subscriptionService;
    private final StringRedisTemplate redisTemplate;
    private final BillingConfigurationService billingConfigurationService;
    private final SubscriptionStatusPayloadService subscriptionStatusPayloadService;
    private final WorkspaceAccessService workspaceAccessService;

    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, Object> payload) {
        if (!workspaceAccessService.isWorkspaceOwner(user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "OWNER_ONLY",
                    "message", "Only the workspace owner can change billing or purchase plans."));
        }
        if (!billingConfigurationService.isPaymentConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "error", "PAYMENT_NOT_CONFIGURED",
                            "message", billingConfigurationService.getUnavailableMessage(),
                            "paymentConfigured", false));
        }
        try {
            int amount = payload.containsKey("amount") ? Integer.parseInt(String.valueOf(payload.get("amount"))) : 39900;
            String plan = amount >= 89900 ? "MAX" : "ACTIVE";

            RazorpayClient client = new RazorpayClient(
                    billingConfigurationService.getKeyId(),
                    billingConfigurationService.getKeySecret());

            JSONObject opts = new JSONObject();
            opts.put("amount", amount);
            opts.put("currency", "INR");
            opts.put("receipt", "rcpt_" + System.currentTimeMillis());
            opts.put("notes", new JSONObject()
                    .put("email", user.getEmail())
                    .put("plan", plan));

            Order order = client.orders.create(opts);
            String orderId = order.get("id");
            log.info("Razorpay order created: {} for user {} for plan {}", orderId, user.getEmail(), plan);

            return ResponseEntity.ok(Map.of(
                    "id", orderId,
                    "orderId", orderId,
                    "amount", order.get("amount"),
                    "currency", order.get("currency"),
                    "plan", plan,
                    "keyId", billingConfigurationService.getKeyId(),
                    "email", user.getEmail()));

        } catch (Exception e) {
            log.error("Failed to create Razorpay order for {}: {}", user.getEmail(), e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Payment order creation failed. Please try again."));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String eventIdHeader) {

        if (!billingConfigurationService.isPaymentConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "error", "PAYMENT_NOT_CONFIGURED",
                            "message", billingConfigurationService.getUnavailableMessage(),
                            "paymentConfigured", false));
        }

        if (signature == null || signature.isBlank()) {
            log.warn("Webhook received without signature - rejected");
            return ResponseEntity.badRequest().build();
        }

        try {
            boolean valid = Utils.verifyWebhookSignature(payload, signature, billingConfigurationService.getWebhookSecret());
            if (!valid) {
                log.warn("Invalid webhook signature - rejected");
                return ResponseEntity.badRequest().build();
            }
        } catch (Exception e) {
            log.error("Webhook signature verification error: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }

        String eventId = buildEventId(eventIdHeader, payload);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(EVENT_PREFIX + eventId))) {
            log.info("Skipping duplicate Razorpay webhook {}", eventId);
            return ResponseEntity.ok(Map.of("status", "already_processed"));
        }

        try {
            JSONObject json = new JSONObject(payload);
            String event = json.getString("event");
            log.info("Razorpay webhook event: {}", event);

            if ("payment.captured".equals(event)) {
                JSONObject paymentEntity = json
                        .getJSONObject("payload")
                        .getJSONObject("payment")
                        .getJSONObject("entity");

                String email = paymentEntity.getJSONObject("notes").optString("email", "");
                String plan = paymentEntity.getJSONObject("notes").optString("plan", "ACTIVE");
                String paymentId = paymentEntity.getString("id");

                if (!email.isBlank()) {
                    subscriptionService.activateSubscription(email, paymentId, plan);
                    log.info("Subscription activated via webhook for email={} to plan={}", email, plan);
                } else {
                    log.warn("Webhook payment.captured missing email in notes - skipped");
                }
            }

            redisTemplate.opsForValue().set(EVENT_PREFIX + eventId, "1", java.time.Duration.ofDays(7));
            return ResponseEntity.ok().build();

        } catch (Exception e) {
            log.error("Error processing webhook payload: {}", e.getMessage());
            return ResponseEntity.ok().build();
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(
            @AuthenticationPrincipal User user,
            @RequestBody Map<String, String> payload) {
        if (!workspaceAccessService.isWorkspaceOwner(user)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                    "error", "OWNER_ONLY",
                    "message", "Only the workspace owner can change billing or purchase plans."));
        }
        if (!billingConfigurationService.isPaymentConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                            "error", "PAYMENT_NOT_CONFIGURED",
                            "message", billingConfigurationService.getUnavailableMessage(),
                            "paymentConfigured", false));
        }
        try {
            String razorpayPaymentId = payload.get("razorpay_payment_id");
            String razorpayOrderId = payload.get("razorpay_order_id");
            String razorpaySignature = payload.get("razorpay_signature");
            String plan = payload.getOrDefault("plan", "ACTIVE");

            if (razorpayPaymentId == null || razorpayOrderId == null || razorpaySignature == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Missing payment verification parameters"));
            }

            JSONObject options = new JSONObject();
            options.put("razorpay_payment_id", razorpayPaymentId);
            options.put("razorpay_order_id", razorpayOrderId);
            options.put("razorpay_signature", razorpaySignature);

            boolean isValid = Utils.verifyPaymentSignature(options, billingConfigurationService.getKeySecret());

            if (isValid) {
                subscriptionService.activateSubscription(user.getEmail(), razorpayPaymentId, plan);
                log.info("Subscription activated via frontend verify for email={} to plan={}", user.getEmail(), plan);
                return ResponseEntity.ok(Map.of("status", "success"));
            }
            log.warn("Invalid payment signature during verify for {}", user.getEmail());
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid payment signature"));
        } catch (Exception e) {
            log.error("Payment verification failed for {}: {}", user.getEmail(), e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("error", "Verification failed"));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(subscriptionStatusPayloadService.build(user, null));
    }

    private String buildEventId(String headerValue, String payload) {
        if (headerValue != null && !headerValue.isBlank()) {
            return headerValue;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            return Integer.toHexString(payload.hashCode());
        }
    }
}
