package com.financeassistant.financeassistant.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BillingConfigurationService {

    @Value("${razorpay.key.id:}")
    private String keyId;

    @Value("${razorpay.key.secret:}")
    private String keySecret;

    @Value("${razorpay.webhook.secret:}")
    private String webhookSecret;

    public boolean isPaymentConfigured() {
        return !keyId.isBlank() && !keySecret.isBlank() && !webhookSecret.isBlank();
    }

    public String getKeyId() {
        return keyId;
    }

    public String getKeySecret() {
        return keySecret;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public String getUnavailableMessage() {
        return "Online payments are unavailable in this environment because Razorpay credentials are not configured.";
    }
}
