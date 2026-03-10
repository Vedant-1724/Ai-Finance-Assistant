package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/team")
@RequiredArgsConstructor
public class TeamInviteController {

    private final TeamService teamService;

    @PostMapping("/accept")
    public ResponseEntity<?> acceptInvite(
            @RequestBody Map<String, String> req,
            @AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Unauthorized"));
        }

        String token = req.get("token");
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invite token is required."));
        }

        teamService.acceptInvite(token, user.getId());
        return ResponseEntity.ok(Map.of("message", "Invite accepted successfully"));
    }
}
