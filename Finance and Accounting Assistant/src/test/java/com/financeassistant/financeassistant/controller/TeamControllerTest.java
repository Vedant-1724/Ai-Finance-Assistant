package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.entity.Company;
import com.financeassistant.financeassistant.entity.CompanyMember;
import com.financeassistant.financeassistant.repository.CompanyRepository;
import com.financeassistant.financeassistant.service.TeamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamControllerTest {

    @Mock
    private TeamService teamService;

    @Mock
    private CompanyRepository companyRepository;

    private TeamController teamController;

    @BeforeEach
    void setUp() {
        teamController = new TeamController(teamService, companyRepository);
    }

    @Test
    void inviteReturnsManualInviteLinkWhenMailIsDisabled() {
        Company company = new Company();
        company.setName("Acme Pvt Ltd");
        when(companyRepository.findById(42L)).thenReturn(Optional.of(company));

        CompanyMember member = new CompanyMember();
        member.setId(99L);

        when(teamService.inviteMember(
                eq(42L),
                eq("Acme Pvt Ltd"),
                eq("invitee@example.com"),
                eq(CompanyMember.Role.VIEWER)))
                .thenReturn(new TeamService.InviteResult(
                        member,
                        "http://localhost:5173/join?token=invite-token",
                        false,
                        "Email delivery is disabled in this environment. Share the invite link manually."));

        ResponseEntity<?> response = teamController.invite(
                42L,
                Map.of("email", "invitee@example.com", "role", "viewer"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertNotNull(body);
        assertEquals(99L, body.get("memberId"));
        assertFalse((Boolean) body.get("emailDeliveryEnabled"));
        assertEquals("http://localhost:5173/join?token=invite-token", body.get("inviteUrl"));
    }
}
