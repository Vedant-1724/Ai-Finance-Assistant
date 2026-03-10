package com.financeassistant.financeassistant.controller;

import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.repository.CompanyRepository;
import com.financeassistant.financeassistant.service.DataDeletionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
public class AccountController {

  private final DataDeletionService dataDeletionService;
  private final CompanyRepository companyRepository;

  @DeleteMapping
  public ResponseEntity<?> deleteAccount(
      HttpServletRequest httpReq,
      HttpServletResponse httpResp,
      @AuthenticationPrincipal User user) {
    if (user == null) {
      return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
    }

    Long companyId = companyRepository.findFirstByOwnerId(user.getId())
        .map(company -> company.getId())
        .orElse(null);

    log.info("Received request to delete account for user: {} from IP: {}", user.getId(), httpReq.getRemoteAddr());

    try {
      dataDeletionService.deleteUserData(user.getId(), companyId);
      ResponseCookie cookie = ResponseCookie.from("jwt_token", "")
          .httpOnly(true)
          .secure(true)
          .path("/")
          .maxAge(0)
          .build();
      httpResp.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
      return ResponseEntity.ok(Map.of("message", "Account and all associated data have been permanently deleted."));
    } catch (Exception e) {
      log.error("Failed to delete account for user: {}", user.getId(), e);
      return ResponseEntity.internalServerError().body(Map.of("error", "Failed to process account deletion."));
    }
  }
}
