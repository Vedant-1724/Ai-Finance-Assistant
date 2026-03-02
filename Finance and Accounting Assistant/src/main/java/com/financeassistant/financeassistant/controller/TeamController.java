package com.financeassistant.financeassistant.controller;
// PATH: TeamController.java
import com.financeassistant.financeassistant.entity.CompanyMember;
import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.repository.CompanyRepository;
import com.financeassistant.financeassistant.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
@RestController @RequestMapping("/api/v1/{companyId}/team") @RequiredArgsConstructor
public class TeamController {
    private final TeamService teamService;
    private final CompanyRepository companyRepo;
    @GetMapping
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<List<CompanyMember>> getMembers(@PathVariable Long companyId) {
        return ResponseEntity.ok(teamService.getMembers(companyId));
    }
    @PostMapping("/invite")
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<?> invite(@PathVariable Long companyId,
            @RequestBody Map<String,String> req) {
        String email = req.get("email");
        String role  = req.getOrDefault("role", "VIEWER");
        String name  = companyRepo.findById(companyId).map(c -> c.getName()).orElse("Your Company");
        CompanyMember m = teamService.inviteMember(companyId, name, email, CompanyMember.Role.valueOf(role.toUpperCase()));
        return ResponseEntity.ok(Map.of("message","Invite sent to " + email, "memberId", m.getId()));
    }
    @PostMapping("/accept")
    public ResponseEntity<?> acceptInvite(@RequestBody Map<String,String> req,
            @AuthenticationPrincipal User user) {
        teamService.acceptInvite(req.get("token"), user.getId());
        return ResponseEntity.ok(Map.of("message","Invite accepted successfully"));
    }
    @PutMapping("/{memberId}/role")
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<?> updateRole(@PathVariable Long companyId,
            @PathVariable Long memberId, @RequestBody Map<String,String> req) {
        teamService.updateRole(companyId, memberId, CompanyMember.Role.valueOf(req.get("role").toUpperCase()));
        return ResponseEntity.ok(Map.of("message","Role updated"));
    }
    @DeleteMapping("/{memberId}")
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<Void> remove(@PathVariable Long companyId, @PathVariable Long memberId) {
        teamService.removeMember(companyId, memberId);
        return ResponseEntity.noContent().build();
    }
}
