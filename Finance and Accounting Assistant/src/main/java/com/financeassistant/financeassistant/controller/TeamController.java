package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.entity.CompanyMember;
import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.repository.CompanyRepository;
import com.financeassistant.financeassistant.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/{companyId}/team")
@RequiredArgsConstructor
public class TeamController {
    private final TeamService teamService;
    private final CompanyRepository companyRepo;

    @GetMapping
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<List<MemberResponse>> getMembers(@PathVariable Long companyId) {
        return ResponseEntity.ok(teamService.getMembers(companyId).stream().map(this::toResponse).toList());
    }

    @PostMapping("/invite")
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<?> invite(@PathVariable Long companyId,
            @RequestBody Map<String, String> req) {
        String email = req.get("email");
        String role = req.getOrDefault("role", "VIEWER");
        String name = companyRepo.findById(companyId).map(c -> c.getName()).orElse("Your Company");
        TeamService.InviteResult result = teamService.inviteMember(companyId, name, email, parseRole(role));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", result.message());
        body.put("memberId", result.member().getId());
        body.put("emailDeliveryEnabled", result.emailDeliveryEnabled());
        if (!result.emailDeliveryEnabled()) {
            body.put("inviteUrl", result.inviteUrl());
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping("/accept")
    public ResponseEntity<?> acceptInvite(@RequestBody Map<String, String> req,
            @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }
        teamService.acceptInvite(req.get("token"), user.getId());
        return ResponseEntity.ok(Map.of("message", "Invite accepted successfully"));
    }

    @PutMapping("/{memberId}/role")
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<?> updateRole(@PathVariable Long companyId,
            @PathVariable Long memberId, @RequestBody Map<String, String> req) {
        teamService.updateRole(companyId, memberId, parseRole(req.getOrDefault("role", "VIEWER")));
        return ResponseEntity.ok(Map.of("message", "Role updated"));
    }

    @DeleteMapping("/{memberId}")
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<Void> remove(@PathVariable Long companyId, @PathVariable Long memberId) {
        teamService.removeMember(companyId, memberId);
        return ResponseEntity.noContent().build();
    }

    private MemberResponse toResponse(CompanyMember member) {
        String email = member.getUser() != null ? member.getUser().getEmail() : null;
        return new MemberResponse(
                member.getId(),
                email,
                normalizeRole(member.getRole()),
                member.getAcceptedAt() != null ? member.getAcceptedAt().toString() : null,
                member.getInviteEmail(),
                member.getCreatedAt() != null ? member.getCreatedAt().toString() : null);
    }

    private CompanyMember.Role parseRole(String role) {
        if (role == null) {
            return CompanyMember.Role.VIEWER;
        }
        return switch (role.trim().toUpperCase()) {
            case "OWNER" -> CompanyMember.Role.OWNER;
            case "EDITOR", "ADMIN", "ACCOUNTANT" -> CompanyMember.Role.EDITOR;
            default -> CompanyMember.Role.VIEWER;
        };
    }

    private String normalizeRole(CompanyMember.Role role) {
        return switch (role) {
            case OWNER -> "OWNER";
            case ADMIN, ACCOUNTANT, EDITOR -> "EDITOR";
            default -> "VIEWER";
        };
    }

    public record MemberResponse(Long id, String email, String role, String acceptedAt,
                                 String inviteEmail, String createdAt) {
    }
}
