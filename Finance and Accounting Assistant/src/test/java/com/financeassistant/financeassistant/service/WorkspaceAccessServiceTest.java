package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.entity.Company;
import com.financeassistant.financeassistant.entity.CompanyMember;
import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.repository.CompanyMemberRepository;
import com.financeassistant.financeassistant.repository.CompanyRepository;
import com.financeassistant.financeassistant.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceAccessServiceTest {

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private CompanyMemberRepository companyMemberRepository;

    @Mock
    private UserRepository userRepository;

    private WorkspaceAccessService workspaceAccessService;

    @BeforeEach
    void setUp() {
        workspaceAccessService = new WorkspaceAccessService(companyRepository, companyMemberRepository, userRepository);
    }

    @Test
    void resolveForOwnerUsesOwnedCompany() {
        User owner = new User("owner@example.com", "encoded", "USER");
        owner.setEmailVerified(true);
        setUserId(owner, 10L);

        Company company = new Company();
        company.setId(42L);
        company.setOwnerId(10L);
        company.setName("Acme");

        when(companyRepository.findFirstByOwnerId(10L)).thenReturn(Optional.of(company));
        when(userRepository.findById(10L)).thenReturn(Optional.of(owner));

        WorkspaceAccessService.WorkspaceContext context = workspaceAccessService.resolveForUser(owner).orElseThrow();

        assertEquals(42L, context.companyId());
        assertEquals(WorkspaceAccessService.WorkspaceRole.OWNER, context.role());
        assertTrue(workspaceAccessService.isCompanyOwner(42L, owner));
    }

    @Test
    void resolveForAcceptedEditorUsesMembershipCompany() {
        User member = new User("editor@example.com", "encoded", "USER");
        setUserId(member, 11L);

        User owner = new User("owner@example.com", "encoded", "USER");
        setUserId(owner, 10L);

        Company company = new Company();
        company.setId(42L);
        company.setOwnerId(10L);
        company.setName("Acme");

        CompanyMember membership = CompanyMember.builder()
                .companyId(42L)
                .userId(11L)
                .role(CompanyMember.Role.ADMIN)
                .acceptedAt(LocalDateTime.now())
                .build();

        when(companyRepository.findFirstByOwnerId(11L)).thenReturn(Optional.empty());
        when(companyMemberRepository.findFirstByUserIdAndAcceptedAtIsNotNullOrderByCreatedAtAsc(11L))
                .thenReturn(Optional.of(membership));
        when(companyRepository.findById(42L)).thenReturn(Optional.of(company));
        when(userRepository.findById(10L)).thenReturn(Optional.of(owner));

        WorkspaceAccessService.WorkspaceContext context = workspaceAccessService.resolveForUser(member).orElseThrow();

        assertEquals(42L, context.companyId());
        assertEquals(WorkspaceAccessService.WorkspaceRole.EDITOR, context.role());
        assertTrue(workspaceAccessService.isCompanyMember(42L, member));
        assertTrue(workspaceAccessService.canEditFinance(42L, member));
        assertFalse(workspaceAccessService.isCompanyOwner(42L, member));
    }

    @Test
    void resolveForAcceptedViewerIsReadOnly() {
        User member = new User("viewer@example.com", "encoded", "USER");
        setUserId(member, 12L);

        User owner = new User("owner@example.com", "encoded", "USER");
        setUserId(owner, 10L);

        Company company = new Company();
        company.setId(42L);
        company.setOwnerId(10L);
        company.setName("Acme");

        CompanyMember membership = CompanyMember.builder()
                .companyId(42L)
                .userId(12L)
                .role(CompanyMember.Role.VIEWER)
                .acceptedAt(LocalDateTime.now())
                .build();

        when(companyRepository.findFirstByOwnerId(12L)).thenReturn(Optional.empty());
        when(companyMemberRepository.findFirstByUserIdAndAcceptedAtIsNotNullOrderByCreatedAtAsc(12L))
                .thenReturn(Optional.of(membership));
        when(companyRepository.findById(42L)).thenReturn(Optional.of(company));
        when(userRepository.findById(10L)).thenReturn(Optional.of(owner));

        WorkspaceAccessService.WorkspaceContext context = workspaceAccessService.resolveForUser(member).orElseThrow();

        assertEquals(WorkspaceAccessService.WorkspaceRole.VIEWER, context.role());
        assertTrue(workspaceAccessService.isCompanyMember(42L, member));
        assertFalse(workspaceAccessService.canEditFinance(42L, member));
    }

    private void setUserId(User user, Long id) {
        try {
            java.lang.reflect.Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException exc) {
            throw new RuntimeException(exc);
        }
    }
}
