package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.entity.Company;
import com.financeassistant.financeassistant.entity.CompanyMember;
import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.repository.CompanyMemberRepository;
import com.financeassistant.financeassistant.repository.CompanyRepository;
import com.financeassistant.financeassistant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WorkspaceAccessService {

    private final CompanyRepository companyRepository;
    private final CompanyMemberRepository companyMemberRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Optional<WorkspaceContext> resolveForUser(User user) {
        if (user == null || user.getId() == null) {
            return Optional.empty();
        }
        return resolveForUserId(user.getId());
    }

    @Transactional(readOnly = true)
    public Optional<WorkspaceContext> resolveForUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }

        Optional<Company> ownedCompany = companyRepository.findFirstByOwnerId(userId);
        if (ownedCompany.isPresent()) {
            return buildContext(ownedCompany.get(), WorkspaceRole.OWNER);
        }

        return companyMemberRepository.findFirstByUserIdAndAcceptedAtIsNotNullOrderByCreatedAtAsc(userId)
                .flatMap(member -> companyRepository.findById(member.getCompanyId())
                        .flatMap(company -> buildContext(company, normalizeRole(member.getRole()))));
    }

    @Transactional(readOnly = true)
    public WorkspaceContext getRequiredWorkspace(User user) {
        return resolveForUser(user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found"));
    }

    @Transactional(readOnly = true)
    public boolean isWorkspaceOwner(User user) {
        return resolveForUser(user)
                .map(WorkspaceContext::isOwner)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isCompanyMember(Long companyId, User user) {
        return resolveForUser(user)
                .map(context -> context.companyId().equals(companyId))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isCompanyOwner(Long companyId, User user) {
        return resolveForUser(user)
                .map(context -> context.companyId().equals(companyId) && context.isOwner())
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean canEditFinance(Long companyId, User user) {
        return resolveForUser(user)
                .map(context -> context.companyId().equals(companyId) && context.role().canEditFinance())
                .orElse(false);
    }

    private Optional<WorkspaceContext> buildContext(Company company, WorkspaceRole role) {
        return userRepository.findById(company.getOwnerId())
                .map(owner -> new WorkspaceContext(
                        company.getId(),
                        company.getName(),
                        company.getOwnerId(),
                        role,
                        owner));
    }

    private WorkspaceRole normalizeRole(CompanyMember.Role role) {
        if (role == null) {
            return WorkspaceRole.VIEWER;
        }

        return switch (role) {
            case OWNER -> WorkspaceRole.OWNER;
            case ADMIN, ACCOUNTANT, EDITOR -> WorkspaceRole.EDITOR;
            case VIEWER -> WorkspaceRole.VIEWER;
        };
    }

    public enum WorkspaceRole {
        OWNER,
        EDITOR,
        VIEWER;

        public boolean canEditFinance() {
            return this == OWNER || this == EDITOR;
        }
    }

    public record WorkspaceContext(
            Long companyId,
            String companyName,
            Long ownerId,
            WorkspaceRole role,
            User workspaceOwner
    ) {
        public boolean isOwner() {
            return role == WorkspaceRole.OWNER;
        }
    }
}
