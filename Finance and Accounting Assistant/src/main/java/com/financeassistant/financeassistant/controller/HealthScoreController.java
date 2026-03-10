package com.financeassistant.financeassistant.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.financeassistant.financeassistant.entity.FinancialHealthScore;
import com.financeassistant.financeassistant.service.FinancialHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/{companyId}/health")
@RequiredArgsConstructor
public class HealthScoreController {
    private final FinancialHealthService healthService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/score")
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<HealthScoreResponse> score(@PathVariable Long companyId,
            @RequestParam(defaultValue = "") String month) {
        LocalDate m = month.isBlank() ? LocalDate.now() : LocalDate.parse(month + "-01");
        FinancialHealthScore entity = healthService.getOrComputeScore(companyId, m);
        Integer previousScore = healthService.getPreviousScore(companyId, entity.getMonth());
        Integer change = previousScore == null ? null : entity.getScore() - previousScore;
        return ResponseEntity.ok(mapToResponse(entity, previousScore, change));
    }

    @GetMapping("/history")
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<List<HealthScoreResponse>> history(@PathVariable Long companyId) {
        List<FinancialHealthScore> history = healthService.getHistory(companyId);
        return ResponseEntity.ok(history.stream().map(entity -> {
            Integer previous = healthService.getPreviousScore(companyId, entity.getMonth());
            Integer change = previous == null ? null : entity.getScore() - previous;
            return mapToResponse(entity, previous, change);
        }).toList());
    }

    private HealthScoreResponse mapToResponse(FinancialHealthScore entity, Integer previousScore, Integer change) {
        String grade = "F";
        if (entity.getScore() >= 80) {
            grade = "A";
        } else if (entity.getScore() >= 60) {
            grade = "B";
        } else if (entity.getScore() >= 40) {
            grade = "C";
        } else if (entity.getScore() >= 20) {
            grade = "D";
        }

        List<BreakdownItem> breakdownList = new ArrayList<>();
        try {
            if (entity.getBreakdown() != null && !entity.getBreakdown().isBlank()) {
                breakdownList = objectMapper.readValue(entity.getBreakdown(), new TypeReference<List<BreakdownItem>>() {
                });
            }
        } catch (JsonProcessingException ignored) {
        }

        return new HealthScoreResponse(
                entity.getScore(),
                grade,
                entity.getMonth().toString().substring(0, 7),
                breakdownList,
                entity.getRecommendations(),
                previousScore,
                change);
    }

    public record BreakdownItem(String label, int score, int weight, String detail) {
    }

    public record HealthScoreResponse(int score, String grade, String month, List<BreakdownItem> breakdown,
            String recommendations, Integer previousScore, Integer change) {
    }
}
