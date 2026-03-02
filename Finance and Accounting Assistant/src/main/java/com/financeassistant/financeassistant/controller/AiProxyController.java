// WHY: Proxies /parse-statement through Spring Boot so:
//  1. JWT auth is enforced before the file ever reaches Python
//  2. Python port 5000 never needs to be publicly accessible
//  3. Company ownership is verified before parsing
//  4. File size/type is validated at the Java layer too

package com.financeassistant.financeassistant.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/v1/{companyId}")
@RequiredArgsConstructor
public class AiProxyController {

    @Value("${ai.service.url:http://localhost:5000}")
    private String aiServiceUrl;

    private final RestTemplate restTemplate;

    // Allowed MIME types — server-side whitelist
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "text/csv", "application/csv",
            "application/pdf",
            "image/png", "image/jpeg", "image/webp", "image/bmp", "image/tiff"
    );

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10MB

    /**
     * POST /api/v1/{companyId}/parse-statement
     *
     * Authenticated proxy to the Python parse-statement endpoint.
     * The user must own the company. File is validated here, then forwarded
     * to Python internally (Python is NOT publicly exposed).
     */
    @PostMapping("/parse-statement")
    @PreAuthorize("@companySecurityService.isOwner(#companyId, authentication)")
    public ResponseEntity<String> parseStatement(
            @PathVariable Long companyId,
            @RequestParam("file") MultipartFile file) throws IOException {

        // Validate file is present
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("{\"error\":\"No file uploaded\"}");
        }

        // Server-side file size check
        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest()
                    .body("{\"error\":\"File too large. Maximum 10MB allowed.\"}");
        }

        // Server-side MIME type check (defense-in-depth)
        String contentType = file.getContentType() != null
                ? file.getContentType().toLowerCase().split(";")[0].trim()
                : "";
        if (!ALLOWED_TYPES.contains(contentType)) {
            // Some browsers send wrong MIME for CSV — also check extension
            String filename = file.getOriginalFilename() != null
                    ? file.getOriginalFilename().toLowerCase() : "";
            boolean isCsv = filename.endsWith(".csv");
            if (!isCsv) {
                log.warn("Rejected file type: {} for company={}", contentType, companyId);
                return ResponseEntity.badRequest()
                        .body("{\"error\":\"Unsupported file type. Use CSV, PDF, PNG, or JPG.\"}");
            }
        }

        // Sanitize filename — prevent path traversal
        String safeFilename = file.getOriginalFilename() != null
                ? file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._\\-]", "_")
                : "upload.bin";

        log.info("Proxying parse-statement for company={}, file={}, size={}",
                companyId, safeFilename, file.getSize());

        // Forward to Python AI service (internal network only)
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() { return safeFilename; }
            };

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", resource);

            HttpEntity<MultiValueMap<String, Object>> requestEntity =
                    new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    aiServiceUrl + "/parse-statement",
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );

            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());

        } catch (Exception e) {
            log.error("AI service unreachable for company={}: {}", companyId, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("{\"error\":\"AI service is unavailable. Please ensure Python is running.\"}");
        }
    }
}