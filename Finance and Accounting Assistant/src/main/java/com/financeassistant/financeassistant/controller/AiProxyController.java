// PATH: finance-backend/src/main/java/com/financeassistant/financeassistant/controller/AiProxyController.java
// FIX: Default AI service URL changed from http://localhost:5000 → http://localhost:5001
//      to match finance-ai/app.py PORT default and Dockerfile EXPOSE 5001.

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

/**
 * Authenticated proxy — routes file uploads from the React frontend
 * to the internal Python AI service.
 *
 * Security:
 * - JWT authentication enforced via Spring Security before this controller runs
 * - @PreAuthorize ensures only the company owner can call these endpoints
 * - Server-side file size + MIME type validation (defense-in-depth on top of
 * frontend checks)
 * - Python service port is NEVER exposed publicly — all traffic goes through
 * here
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/{companyId}")
@RequiredArgsConstructor
public class AiProxyController {

        @Value("${ai.service.url:http://localhost:5001}")
        private String aiServiceUrl;

        @Value("${ai.service.api.key}")
        private String aiServiceApiKey;

        private final RestTemplate restTemplate;

        // ── Server-side allow-list for uploaded file MIME types ───────────────────
        private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
                        "text/csv",
                        "application/csv",
                        "application/pdf",
                        "image/png",
                        "image/jpeg",
                        "image/webp",
                        "image/bmp",
                        "image/tiff",
                        "image/heic",
                        "image/heif");

        private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
                        "csv", "pdf", "png", "jpg", "jpeg", "webp", "bmp", "tiff", "heic", "heif");

        private static final long MAX_FILE_BYTES = 10L * 1024 * 1024; // 10 MB

        // ─────────────────────────────────────────────────────────────────────────
        // POST /api/v1/{companyId}/parse-statement
        // ─────────────────────────────────────────────────────────────────────────
        /**
         * Authenticated proxy: validates the uploaded statement file,
         * then forwards it to Python /parse-statement.
         * Python returns a list of redacted transactions for the user to review.
         */
        @PostMapping("/parse-statement")
        @PreAuthorize("@companySecurityService.canEditFinance(#companyId, authentication)")
        public ResponseEntity<String> parseStatement(
                        @PathVariable Long companyId,
                        @RequestParam("file") MultipartFile file) throws IOException {

                // 1. Null / empty check
                if (file == null || file.isEmpty()) {
                        return badRequest("No file uploaded.");
                }

                // 2. Size check
                if (file.getSize() > MAX_FILE_BYTES) {
                        return badRequest("File too large. Maximum 10 MB allowed.");
                }

                // 3. MIME type check
                String contentType = file.getContentType() != null
                                ? file.getContentType().toLowerCase().split(";")[0].trim()
                                : "";

                String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
                String extension = filename.contains(".")
                                ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase()
                                : "";

                boolean mimeOk = ALLOWED_CONTENT_TYPES.contains(contentType);
                boolean extOk = ALLOWED_EXTENSIONS.contains(extension);

                // Accept if either MIME or extension matches (browsers send wrong MIME for CSV)
                if (!mimeOk && !extOk) {
                        log.warn("Rejected file upload: contentType='{}', extension='{}', company={}",
                                        contentType, extension, companyId);
                        return badRequest(
                                        "Unsupported file type. Allowed: CSV, PDF, PNG, JPG, WEBP, BMP, TIFF, HEIC.");
                }

                // 4. Forward to Python AI service
                log.info("Forwarding parse-statement: company={}, file='{}', size={}B, type='{}'",
                                companyId, filename, file.getSize(), contentType);

                try {
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                        headers.set("X-API-Key", aiServiceApiKey);

                        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                        body.add("file", new ByteArrayResource(file.getBytes()) {
                                @Override
                                public String getFilename() {
                                        return filename;
                                }
                        });

                        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

                        // FIX: aiServiceUrl now defaults to http://localhost:5001
                        ResponseEntity<String> response = restTemplate.exchange(
                                        aiServiceUrl + "/parse-statement",
                                        HttpMethod.POST,
                                        request,
                                        String.class);

                        return ResponseEntity
                                        .status(response.getStatusCode())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(response.getBody());

                } catch (Exception e) {
                        log.error("AI parse-statement error for company {}: {}", companyId, e.getMessage());
                        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                        .body("{\"error\":\"AI service is unavailable. "
                                                        + "Ensure the Python service is running on port 5001.\"}");
                }
        }

        // ─────────────────────────────────────────────────────────────────────────
        // POST /api/v1/{companyId}/ocr
        // ─────────────────────────────────────────────────────────────────────────
        /**
         * Authenticated proxy: forwards invoice image to Python /ocr endpoint.
         */
        @PostMapping("/ocr")
        @PreAuthorize("@companySecurityService.canEditFinance(#companyId, authentication)")
        public ResponseEntity<String> ocrInvoice(
                        @PathVariable Long companyId,
                        @RequestParam("file") MultipartFile file) throws IOException {

                if (file == null || file.isEmpty()) {
                        return badRequest("No file uploaded.");
                }
                if (file.getSize() > MAX_FILE_BYTES) {
                        return badRequest("File too large. Maximum 10 MB allowed.");
                }

                log.info("Forwarding OCR: company={}, file='{}', size={}B",
                                companyId, file.getOriginalFilename(), file.getSize());

                try {
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                        headers.set("X-API-Key", aiServiceApiKey);

                        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "invoice";
                        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                        body.add("file", new ByteArrayResource(file.getBytes()) {
                                @Override
                                public String getFilename() {
                                        return filename;
                                }
                        });

                        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

                        ResponseEntity<String> response = restTemplate.exchange(
                                        aiServiceUrl + "/ocr",
                                        HttpMethod.POST,
                                        request,
                                        String.class);

                        return ResponseEntity
                                        .status(response.getStatusCode())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(response.getBody());

                } catch (Exception e) {
                        log.error("OCR error for company {}: {}", companyId, e.getMessage());
                        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                                        .body("{\"error\":\"OCR service is unavailable.\"}");
                }
        }

        // ── Helper ────────────────────────────────────────────────────────────────
        private static ResponseEntity<String> badRequest(String message) {
                return ResponseEntity.badRequest()
                                .contentType(MediaType.APPLICATION_JSON)
                                .body("{\"error\":\"" + message.replace("\"", "'") + "\"}");
        }
}
