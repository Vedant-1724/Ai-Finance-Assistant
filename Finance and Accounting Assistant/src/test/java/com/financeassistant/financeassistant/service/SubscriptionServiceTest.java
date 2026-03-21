package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private WorkspaceAccessService workspaceAccessService;

    private SubscriptionService subscriptionService;

    @BeforeEach
    void setUp() {
        subscriptionService = new SubscriptionService(userRepository, workspaceAccessService);
    }

    @Test
    void startTrialStartsForFreeUserWithUnusedTrial() {
        User user = new User("free@example.com", "encoded", "USER");
        when(workspaceAccessService.isWorkspaceOwner(user)).thenReturn(true);
        when(workspaceAccessService.getRequiredWorkspace(user)).thenReturn(
                new WorkspaceAccessService.WorkspaceContext(1L, "Acme", user.getId(), WorkspaceAccessService.WorkspaceRole.OWNER, user));
        when(userRepository.save(user)).thenReturn(user);

        SubscriptionService.TrialStartResult result = subscriptionService.startTrial(user);

        assertEquals(SubscriptionService.TrialStartResult.STARTED, result);
        assertEquals(User.SubscriptionStatus.TRIAL, user.getSubscriptionStatus());
        assertNotNull(user.getTrialStartedAt());
        verify(userRepository).save(user);
    }

    @Test
    void startTrialFailsWhenTrialAlreadyUsed() {
        User user = new User("free@example.com", "encoded", "USER");
        user.setTrialStartedAt(Instant.now().minus(5, ChronoUnit.DAYS));
        when(workspaceAccessService.isWorkspaceOwner(user)).thenReturn(true);
        when(workspaceAccessService.getRequiredWorkspace(user)).thenReturn(
                new WorkspaceAccessService.WorkspaceContext(1L, "Acme", user.getId(), WorkspaceAccessService.WorkspaceRole.OWNER, user));

        SubscriptionService.TrialStartResult result = subscriptionService.startTrial(user);

        assertEquals(SubscriptionService.TrialStartResult.ALREADY_USED, result);
        verify(userRepository, never()).save(any());
    }

    @Test
    void startTrialFailsForTrialUsers() {
        User user = new User("trial@example.com", "encoded", "USER");
        user.setSubscriptionStatus(User.SubscriptionStatus.TRIAL);
        user.setTrialStartedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(workspaceAccessService.isWorkspaceOwner(user)).thenReturn(true);
        when(workspaceAccessService.getRequiredWorkspace(user)).thenReturn(
                new WorkspaceAccessService.WorkspaceContext(1L, "Acme", user.getId(), WorkspaceAccessService.WorkspaceRole.OWNER, user));

        SubscriptionService.TrialStartResult result = subscriptionService.startTrial(user);

        assertEquals(SubscriptionService.TrialStartResult.FREE_TIER_ONLY, result);
        verify(userRepository, never()).save(any());
    }

    @Test
    void startTrialFailsForProUsers() {
        User user = new User("pro@example.com", "encoded", "USER");
        user.setSubscriptionStatus(User.SubscriptionStatus.ACTIVE);
        user.setSubscriptionExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        when(workspaceAccessService.isWorkspaceOwner(user)).thenReturn(true);
        when(workspaceAccessService.getRequiredWorkspace(user)).thenReturn(
                new WorkspaceAccessService.WorkspaceContext(1L, "Acme", user.getId(), WorkspaceAccessService.WorkspaceRole.OWNER, user));

        SubscriptionService.TrialStartResult result = subscriptionService.startTrial(user);

        assertEquals(SubscriptionService.TrialStartResult.FREE_TIER_ONLY, result);
        verify(userRepository, never()).save(any());
    }

    @Test
    void startTrialFailsForMaxUsers() {
        User user = new User("max@example.com", "encoded", "USER");
        user.setSubscriptionStatus(User.SubscriptionStatus.MAX);
        user.setSubscriptionExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        when(workspaceAccessService.isWorkspaceOwner(user)).thenReturn(true);
        when(workspaceAccessService.getRequiredWorkspace(user)).thenReturn(
                new WorkspaceAccessService.WorkspaceContext(1L, "Acme", user.getId(), WorkspaceAccessService.WorkspaceRole.OWNER, user));

        SubscriptionService.TrialStartResult result = subscriptionService.startTrial(user);

        assertEquals(SubscriptionService.TrialStartResult.FREE_TIER_ONLY, result);
        verify(userRepository, never()).save(any());
    }

    @Test
    void startTrialFailsForNonOwners() {
        User user = new User("member@example.com", "encoded", "USER");
        when(workspaceAccessService.isWorkspaceOwner(user)).thenReturn(false);

        SubscriptionService.TrialStartResult result = subscriptionService.startTrial(user);

        assertEquals(SubscriptionService.TrialStartResult.OWNER_ONLY, result);
        verify(userRepository, never()).save(any());
    }
}
