package com.financeassistant.financeassistant.service;

import com.financeassistant.financeassistant.entity.Anomaly;
import com.financeassistant.financeassistant.entity.Company;
import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.repository.CompanyRepository;
import com.financeassistant.financeassistant.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * EmailAlertService
 *
 * Sends an HTML email to the company owner whenever anomalies are detected.
 *
 * ── How it plugs in ──────────────────────────────────────────────────────────
 *   AnomalyResultListener (RabbitMQ consumer)
 *       → saves anomalies to DB
 *       → calls sendAnomalyAlert(companyId, anomalies)   ← this class
 *
 * ── Graceful degradation ─────────────────────────────────────────────────────
 *   JavaMailSender is @Autowired(required = false) so the app starts even
 *   when no mail server is configured (e.g. local dev without .env mail vars).
 *   If mail is not configured, a WARN is logged and the method returns silently.
 *
 * ── Async ────────────────────────────────────────────────────────────────────
 *   @Async ensures sending the email never blocks the RabbitMQ consumer thread.
 *   The consumer acks the message and moves on instantly.
 *   Add @EnableAsync to any @Configuration class (already done in MailConfig).
 *
 * Place at:
 *   Finance and Accounting Assistant/src/main/java/com/financeassistant/
 *   financeassistant/service/EmailAlertService.java
 */
@Slf4j
@Service
public class EmailAlertService {

    // required = false → app starts even if mail is not configured
    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Value("${app.name:AI Finance Assistant}")
    private String appName;

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    /**
     * Called by AnomalyResultListener after saving anomalies to the DB.
     * Runs on a separate thread (@Async) so the RabbitMQ consumer is not blocked.
     *
     * @param companyId  the company whose owner should be alerted
     * @param anomalies  list of newly detected Anomaly entities (already saved to DB)
     */
    @Async
    public void sendAnomalyAlert(Long companyId, List<Anomaly> anomalies) {

        // ── Guard: mail not configured ────────────────────────────────────────
        if (mailSender == null) {
            log.warn("Mail sender not configured — skipping email alert for company={}", companyId);
            return;
        }

        if (anomalies == null || anomalies.isEmpty()) {
            return;
        }

        // ── Resolve recipient email ───────────────────────────────────────────
        Optional<Company> companyOpt = companyRepository.findById(companyId);
        if (companyOpt.isEmpty()) {
            log.warn("Company {} not found — cannot send anomaly alert email", companyId);
            return;
        }

        Company company = companyOpt.get();
        Optional<User> userOpt = userRepository.findById(company.getOwnerId());
        if (userOpt.isEmpty()) {
            log.warn("Owner (id={}) not found for company {} — cannot send email",
                    company.getOwnerId(), companyId);
            return;
        }

        String recipientEmail = userOpt.get().getEmail();

        // ── Build and send email ──────────────────────────────────────────────
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(recipientEmail);
            helper.setSubject(buildSubject(anomalies.size(), company.getName()));
            helper.setText(buildHtmlBody(anomalies, company), true);  // true = HTML

            mailSender.send(message);
            log.info("Anomaly alert sent to {} for company='{}' — {} anomaly/anomalies",
                    recipientEmail, company.getName(), anomalies.size());

        } catch (Exception e) {
            // Email failure must never crash the anomaly pipeline
            log.error("Failed to send anomaly alert email to {}: {}", recipientEmail, e.getMessage());
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private String buildSubject(int count, String companyName) {
        return String.format("🚨 [%s] %d Anomaly%s Detected in %s",
                appName, count, count == 1 ? "" : " Alerts", companyName);
    }

    private String buildHtmlBody(List<Anomaly> anomalies, Company company) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head>")
          .append("<meta charset='UTF-8'>")
          .append("<style>")
          .append("  body { font-family: Arial, sans-serif; background: #f4f4f4; margin: 0; padding: 20px; }")
          .append("  .container { max-width: 600px; margin: 0 auto; background: #fff; border-radius: 8px;")
          .append("               box-shadow: 0 2px 8px rgba(0,0,0,0.1); overflow: hidden; }")
          .append("  .header { background: #1a1a2e; color: #e94560; padding: 24px 32px; }")
          .append("  .header h1 { margin: 0; font-size: 22px; }")
          .append("  .header p  { margin: 4px 0 0; color: #aaa; font-size: 13px; }")
          .append("  .body { padding: 24px 32px; }")
          .append("  .summary { background: #fff3cd; border-left: 4px solid #ffc107;")
          .append("             padding: 12px 16px; border-radius: 4px; margin-bottom: 20px; }")
          .append("  .anomaly-card { background: #fff5f5; border: 1px solid #fed7d7;")
          .append("                  border-radius: 6px; padding: 14px 18px; margin-bottom: 12px; }")
          .append("  .anomaly-amount { font-size: 20px; font-weight: bold; color: #e53e3e; }")
          .append("  .anomaly-meta   { font-size: 12px; color: #718096; margin-top: 4px; }")
          .append("  .footer { background: #f7fafc; padding: 16px 32px; font-size: 12px;")
          .append("            color: #a0aec0; border-top: 1px solid #e2e8f0; }")
          .append("  .btn { display: inline-block; background: #e94560; color: #fff;")
          .append("         padding: 10px 24px; border-radius: 4px; text-decoration: none;")
          .append("         font-weight: bold; margin-top: 16px; }")
          .append("</style></head><body>")
          .append("<div class='container'>")

          // Header
          .append("<div class='header'>")
          .append("<h1>🚨 Anomaly Alert</h1>")
          .append("<p>").append(appName).append(" — ").append(company.getName()).append("</p>")
          .append("</div>")

          // Body
          .append("<div class='body'>")
          .append("<div class='summary'>")
          .append("<strong>⚠️ ").append(anomalies.size()).append(" unusual transaction")
          .append(anomalies.size() > 1 ? "s have" : " has")
          .append(" been detected</strong> in your account by the AI anomaly detection engine.")
          .append("</div>");

        // Individual anomaly cards
        for (Anomaly a : anomalies) {
            String amountStr = formatAmount(a.getAmount(), company.getCurrency());
            String detectedAt = a.getDetectedAt() != null
                    ? a.getDetectedAt().format(FORMATTER) : "Just now";

            sb.append("<div class='anomaly-card'>")
              .append("<div class='anomaly-amount'>").append(amountStr).append("</div>")
              .append("<div class='anomaly-meta'>")
              .append("Transaction #").append(a.getTransactionId() != null ? a.getTransactionId() : "N/A")
              .append(" &nbsp;·&nbsp; Detected: ").append(detectedAt)
              .append("</div>")
              .append("</div>");
        }

        sb.append("<p style='color:#555;font-size:14px;margin-top:20px;'>")
          .append("Please review these transactions in your dashboard. ")
          .append("If they look correct, you can dismiss the alerts. ")
          .append("If not, take appropriate action immediately.")
          .append("</p>")
          .append("<a href='http://localhost:5173' class='btn'>Open Dashboard →</a>")
          .append("</div>")

          // Footer
          .append("<div class='footer'>")
          .append("This is an automated alert from ").append(appName).append(". ")
          .append("You are receiving this because you are the owner of <strong>")
          .append(company.getName()).append("</strong>.")
          .append("</div>")
          .append("</div></body></html>");

        return sb.toString();
    }

    private String formatAmount(BigDecimal amount, String currency) {
        if (amount == null) return "N/A";
        String symbol = "INR".equalsIgnoreCase(currency) ? "₹" :
                        "USD".equalsIgnoreCase(currency) ? "$" : currency + " ";
        return symbol + String.format("%,.2f", amount.abs());
    }
}
