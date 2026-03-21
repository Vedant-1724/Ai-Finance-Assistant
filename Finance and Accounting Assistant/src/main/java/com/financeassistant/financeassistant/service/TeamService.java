package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.entity.CompanyMember;
import com.financeassistant.financeassistant.repository.CompanyRepository;
import com.financeassistant.financeassistant.repository.CompanyMemberRepository;
import com.financeassistant.financeassistant.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamService {

    private final CompanyMemberRepository memberRepo;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepo;
    private final EmailAlertService emailAlertService;

    @Value("${app.base-url:http://localhost:5173}")
    private String baseUrl;

    @Transactional
    public InviteResult inviteMember(Long companyId, String companyName, String email, CompanyMember.Role role) {
        userRepo.findByEmail(email).ifPresent(u -> {
            if (memberRepo.existsByCompanyIdAndUserId(companyId, u.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "User is already a member");
            }
        });

        String token = UUID.randomUUID().toString();
        CompanyMember member = new CompanyMember();
        member.setCompanyId(companyId);
        member.setRole(role);
        member.setInviteEmail(email);
        member.setInviteToken(token);
        member.setInviteExpiresAt(LocalDateTime.now().plusHours(72));

        CompanyMember saved = memberRepo.save(member);

        String inviteUrl = emailAlertService.buildInviteUrl(token);
        emailAlertService.sendTeamInvite(email, companyName, inviteUrl);

        String message = emailAlertService.isMailEnabled()
                ? "Invite sent to " + email
                : "Email delivery is disabled in this environment. Share the invite link manually.";

        return new InviteResult(saved, inviteUrl, emailAlertService.isMailEnabled(), message);
    }

    @Transactional
    public void acceptInvite(String token, Long userId) {
        CompanyMember member = memberRepo.findByInviteToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invalid invite token"));

        if (member.getInviteExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Invite token has expired");
        }

        member.setUserId(userId);
        member.setAcceptedAt(LocalDateTime.now());
        member.setInviteToken(null);
        memberRepo.save(member);
    }

    public List<CompanyMember> getMembers(Long companyId) {
        ensureOwnerMembership(companyId);
        return memberRepo.findByCompanyIdOrderByCreatedAtAsc(companyId);
    }

    @Transactional
    public void updateRole(Long companyId, Long memberId, CompanyMember.Role newRole) {
        CompanyMember m = memberRepo.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        if (!m.getCompanyId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        if (m.getRole() == CompanyMember.Role.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot change owner role");
        }
        m.setRole(newRole);
        memberRepo.save(m);
    }

    @Transactional
    public void removeMember(Long companyId, Long memberId) {
        CompanyMember m = memberRepo.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        if (!m.getCompanyId().equals(companyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        if (m.getRole() == CompanyMember.Role.OWNER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot remove owner");
        }
        memberRepo.delete(m);
    }

    public record InviteResult(CompanyMember member, String inviteUrl, boolean emailDeliveryEnabled, String message) {
    }

    private void ensureOwnerMembership(Long companyId) {
        companyRepository.findById(companyId).ifPresent(company -> {
            if (!memberRepo.existsByCompanyIdAndUserId(companyId, company.getOwnerId())) {
                CompanyMember ownerMembership = new CompanyMember();
                ownerMembership.setCompanyId(companyId);
                ownerMembership.setUserId(company.getOwnerId());
                ownerMembership.setRole(CompanyMember.Role.OWNER);
                ownerMembership.setAcceptedAt(LocalDateTime.now());
                memberRepo.save(ownerMembership);
            }
        });
    }
}
