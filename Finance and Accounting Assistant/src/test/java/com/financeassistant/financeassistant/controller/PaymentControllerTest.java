package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.service.BillingConfigurationService;
import com.financeassistant.financeassistant.service.SubscriptionService;
import com.financeassistant.financeassistant.service.SubscriptionStatusPayloadService;
import com.financeassistant.financeassistant.service.WorkspaceAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private BillingConfigurationService billingConfigurationService;

    @Mock
    private SubscriptionStatusPayloadService subscriptionStatusPayloadService;

    @Mock
    private WorkspaceAccessService workspaceAccessService;

    private PaymentController paymentController;

    @BeforeEach
    void setUp() {
        paymentController = new PaymentController(
                subscriptionService,
                redisTemplate,
                billingConfigurationService,
                subscriptionStatusPayloadService,
                workspaceAccessService);
    }

    @Test
    void statusIncludesPaymentConfigurationFlags() {
        User user = new User("owner@example.com", "encoded", "USER");
        when(subscriptionStatusPayloadService.build(user, null)).thenReturn(Map.of(
                "trialEligible", true,
                "paymentConfigured", false,
                "paymentMessage", "Online payments are unavailable in this environment because Razorpay credentials are not configured."));

        ResponseEntity<?> response = paymentController.getStatus(user);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertTrue((Boolean) body.get("trialEligible"));
        assertFalse((Boolean) body.get("paymentConfigured"));
        assertEquals("Online payments are unavailable in this environment because Razorpay credentials are not configured.", body.get("paymentMessage"));
    }

    @Test
    void createOrderReturnsServiceUnavailableWhenPaymentIsDisabled() {
        User user = new User("owner@example.com", "encoded", "USER");
        when(workspaceAccessService.isWorkspaceOwner(user)).thenReturn(true);
        when(billingConfigurationService.isPaymentConfigured()).thenReturn(false);
        when(billingConfigurationService.getUnavailableMessage())
                .thenReturn("Online payments are unavailable in this environment because Razorpay credentials are not configured.");

        ResponseEntity<?> response = paymentController.createOrder(user, Map.of("amount", 39900));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals("PAYMENT_NOT_CONFIGURED", body.get("error"));
        assertFalse((Boolean) body.get("paymentConfigured"));
    }

    @Test
    void createOrderRejectsNonOwners() {
        User user = new User("member@example.com", "encoded", "USER");
        when(workspaceAccessService.isWorkspaceOwner(user)).thenReturn(false);

        ResponseEntity<?> response = paymentController.createOrder(user, Map.of("amount", 39900));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals("OWNER_ONLY", body.get("error"));
    }
}
