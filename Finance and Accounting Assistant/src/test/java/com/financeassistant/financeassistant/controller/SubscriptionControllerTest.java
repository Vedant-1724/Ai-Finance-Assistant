package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.service.SubscriptionService;
import com.financeassistant.financeassistant.service.SubscriptionStatusPayloadService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionControllerTest {

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private SubscriptionStatusPayloadService subscriptionStatusPayloadService;

    private SubscriptionController subscriptionController;

    @BeforeEach
    void setUp() {
        subscriptionController = new SubscriptionController(
                subscriptionService,
                subscriptionStatusPayloadService);
        lenient().when(subscriptionStatusPayloadService.build(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Map.of(
                        "trialEligible", true,
                        "paymentConfigured", false,
                        "paymentMessage", "Online payments are unavailable in this environment because Razorpay credentials are not configured."));
    }

    @AfterEach
    void tearDown() {
        org.mockito.Mockito.framework().clearInlineMocks();
    }

    @Test
    void startTrialRejectsPaidUsers() {
        User user = new User("owner@example.com", "encoded", "USER");
        when(subscriptionService.startTrial(user)).thenReturn(SubscriptionService.TrialStartResult.FREE_TIER_ONLY);

        ResponseEntity<?> response = subscriptionController.startTrial(user);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals("TRIAL_FREE_TIER_ONLY", body.get("error"));
    }

    @Test
    void startTrialRejectsUsedTrials() {
        User user = new User("owner@example.com", "encoded", "USER");
        when(subscriptionService.startTrial(user)).thenReturn(SubscriptionService.TrialStartResult.ALREADY_USED);

        ResponseEntity<?> response = subscriptionController.startTrial(user);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals("TRIAL_ALREADY_USED", body.get("error"));
    }

    @Test
    void statusIncludesPaymentConfigurationFlagsAndTrialEligibilityForFreeUser() {
        User user = new User("owner@example.com", "encoded", "USER");

        ResponseEntity<?> response = subscriptionController.getStatus(user);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals(true, body.get("trialEligible"));
        assertEquals(false, body.get("paymentConfigured"));
        assertEquals("Online payments are unavailable in this environment because Razorpay credentials are not configured.", body.get("paymentMessage"));
    }

    @Test
    void startTrialRejectsNonOwners() {
        User user = new User("owner@example.com", "encoded", "USER");
        when(subscriptionService.startTrial(user)).thenReturn(SubscriptionService.TrialStartResult.OWNER_ONLY);

        ResponseEntity<?> response = subscriptionController.startTrial(user);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals("OWNER_ONLY", body.get("error"));
    }
}
