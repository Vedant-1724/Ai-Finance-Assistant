package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.security.JwtUtil;
import com.financeassistant.financeassistant.service.DataDeletionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller for managing the user's account lifecycle (Data Compliance).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
public class AccountController {

  private final DataDeletionService dataDeletionService;
  private final JwtUtil jwtUtil;

  /**
   * DELETE /api/v1/account
   * Deletes the authenticated user's account and all associated data.
   */
  @DeleteMapping
  public ResponseEntity<?> deleteAccount(HttpServletRequest httpReq, @AuthenticationPrincipal User user) {
    if (user == null) {
      return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
    }

    // Extract companyId from token
    String authHeader = httpReq.getHeader("Authorization");
    Long companyId = null;
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String token = authHeader.substring(7);
      companyId = jwtUtil.extractCompanyId(token);
    }

    log.info("Received request to delete account for user: {}", user.getId());

    try {
      dataDeletionService.deleteUserData(user.getId(), companyId);
      return ResponseEntity.ok(Map.of("message", "Account and all associated data have been permanently deleted."));
    } catch (Exception e) {
      log.error("Failed to delete account for user: {}", user.getId(), e);
      return ResponseEntity.internalServerError().body(Map.of("error", "Failed to process account deletion."));
    }
  }
}
