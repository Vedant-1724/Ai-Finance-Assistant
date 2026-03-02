package com.financeassistant.financeassistant.service;

// PATH: Finance and Accounting Assistant/src/main/java/com/financeassistant/financeassistant/service/TeamService.java

import com.financeassistant.financeassistant.entity.CompanyMember;
import com.financeassistant.financeassistant.entity.User;
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
    private final UserRepository userRepo;
    private final EmailAlertService emailAlertService;

    @Value("${app.base-url:http://localhost:5173}")
    private String baseUrl;

    @Transactional
    public CompanyMember inviteMember(Long companyId, String companyName,
                                       String email, CompanyMember.Role role) {
        // Check not already a member
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

        String inviteUrl = baseUrl + "/join?token=" + token;
        emailAlertService.sendTeamInvite(email, companyName, inviteUrl);

        return saved;
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
        member.setInviteToken(null);   // consume token
        memberRepo.save(member);
    }

    public List<CompanyMember> getMembers(Long companyId) {
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
}
