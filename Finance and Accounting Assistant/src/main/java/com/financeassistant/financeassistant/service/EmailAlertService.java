package com.financeassistant.financeassistant.service;

// PATH: Finance and Accounting Assistant/src/main/java/com/financeassistant/financeassistant/service/EmailAlertService.java

import com.financeassistant.financeassistant.entity.User;
import com.financeassistant.financeassistant.entity.UserEmailPrefs;
import com.financeassistant.financeassistant.repository.UserEmailPrefsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;

/**
 * EmailAlertService
 * Sends async HTML emails for: anomaly alerts, forecast warnings,
 * budget threshold warnings, trial expiry, and weekly summaries.
 *
 * All sends are @Async — they never block the caller thread.
 * Set app.mail.enabled=true in application.yaml to activate in production.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailAlertService {

    private final JavaMailSender mailSender;
    private final UserEmailPrefsRepository prefsRepo;

    @Value("${app.mail.from:noreply@financeai.in}")
    private String fromAddress;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    // ── Anomaly Alert ─────────────────────────────────────────────────────────
    @Async
    public void sendAnomalyAlert(User user, BigDecimal amount, String description) {
        if (!mailEnabled) return;
        UserEmailPrefs prefs = prefsRepo.findByUserId(user.getId()).orElse(null);
        if (prefs != null && !prefs.isAnomalyAlerts()) return;

        String subject = "⚠️ Unusual Transaction Detected — FinanceAI";
        String html = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;background:#0f172a;color:#e2e8f0;padding:32px;border-radius:12px">
              <h2 style="color:#f59e0b;margin-top:0">⚠️ Unusual Transaction Detected</h2>
              <p>Hi %s,</p>
              <p>Our AI detected an unusual transaction in your account:</p>
              <div style="background:#1e293b;border-left:4px solid #f59e0b;padding:16px;border-radius:8px;margin:16px 0">
                <strong>Amount:</strong> ₹%s<br/>
                <strong>Description:</strong> %s
              </div>
              <p>Please review this transaction in your <a href="https://financeai.in/dashboard" style="color:#3b82f6">dashboard</a>.</p>
              <p style="color:#64748b;font-size:12px">You can manage alert preferences in Settings → Notifications.</p>
            </div>
            """.formatted(user.getEmail(), amount.toPlainString(), description);

        sendEmail(user.getEmail(), subject, html);
    }

    // ── Negative Forecast Alert ───────────────────────────────────────────────
    @Async
    public void sendNegativeForecastAlert(User user, int daysUntilNegative, BigDecimal projectedBalance) {
        if (!mailEnabled) return;
        UserEmailPrefs prefs = prefsRepo.findByUserId(user.getId()).orElse(null);
        if (prefs != null && !prefs.isForecastAlerts()) return;

        String subject = "🔮 Cash Flow Warning — FinanceAI";
        String html = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;background:#0f172a;color:#e2e8f0;padding:32px;border-radius:12px">
              <h2 style="color:#ef4444;margin-top:0">🔮 Cash Flow Warning</h2>
              <p>Hi %s,</p>
              <p>Your AI cash flow forecast shows a potential issue:</p>
              <div style="background:#1e293b;border-left:4px solid #ef4444;padding:16px;border-radius:8px;margin:16px 0">
                <strong>Projected balance turns negative in:</strong> %d days<br/>
                <strong>Projected balance:</strong> ₹%s
              </div>
              <p>View your full <a href="https://financeai.in/dashboard" style="color:#3b82f6">cash flow forecast</a> and take action now.</p>
            </div>
            """.formatted(user.getEmail(), daysUntilNegative, projectedBalance.toPlainString());

        sendEmail(user.getEmail(), subject, html);
    }

    // ── Budget Threshold Alert (90%+) ─────────────────────────────────────────
    @Async
    public void sendBudgetAlert(User user, String categoryName, BigDecimal spent, BigDecimal budget) {
        if (!mailEnabled) return;
        UserEmailPrefs prefs = prefsRepo.findByUserId(user.getId()).orElse(null);
        if (prefs != null && !prefs.isBudgetAlerts()) return;

        int pct = budget.compareTo(BigDecimal.ZERO) == 0 ? 0 :
                  spent.multiply(BigDecimal.valueOf(100)).divide(budget, 0, java.math.RoundingMode.HALF_UP).intValue();
        String subject = "📊 Budget Alert: " + categoryName + " at " + pct + "% — FinanceAI";
        String html = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;background:#0f172a;color:#e2e8f0;padding:32px;border-radius:12px">
              <h2 style="color:#f59e0b;margin-top:0">📊 Budget Threshold Alert</h2>
              <p>Hi %s,</p>
              <p>Your <strong>%s</strong> budget is at <strong style="color:#f59e0b">%d%%</strong>:</p>
              <div style="background:#1e293b;padding:16px;border-radius:8px;margin:16px 0">
                <div style="display:flex;justify-content:space-between">
                  <span>Spent: ₹%s</span><span>Budget: ₹%s</span>
                </div>
                <div style="background:#334155;border-radius:4px;height:8px;margin-top:8px">
                  <div style="background:%s;border-radius:4px;height:8px;width:%d%%"></div>
                </div>
              </div>
              <p>Review your <a href="https://financeai.in/dashboard" style="color:#3b82f6">budget planner</a>.</p>
            </div>
            """.formatted(user.getEmail(), categoryName, pct,
                         spent.toPlainString(), budget.toPlainString(),
                         pct >= 100 ? "#ef4444" : "#f59e0b", Math.min(pct, 100));

        sendEmail(user.getEmail(), subject, html);
    }

    // ── Trial Expiry Reminder ─────────────────────────────────────────────────
    @Async
    public void sendTrialExpiryReminder(User user, long daysRemaining) {
        if (!mailEnabled) return;
        UserEmailPrefs prefs = prefsRepo.findByUserId(user.getId()).orElse(null);
        if (prefs != null && !prefs.isTrialReminders()) return;

        String subject = daysRemaining == 1 ? "⏰ Last Day of Your Trial — FinanceAI"
                       : "⏰ " + daysRemaining + " Days Left in Your Trial — FinanceAI";
        String html = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;background:#0f172a;color:#e2e8f0;padding:32px;border-radius:12px">
              <h2 style="color:#3b82f6;margin-top:0">⏰ Your Free Trial Ends Soon</h2>
              <p>Hi %s,</p>
              <p>You have <strong style="color:#f59e0b">%d day%s</strong> left on your FinanceAI Pro trial.</p>
              <p>To keep access to:</p>
              <ul style="color:#94a3b8">
                <li>AI Cash Flow Forecasting</li><li>Anomaly Detection</li>
                <li>Invoice OCR</li><li>Full P&amp;L Reports</li>
              </ul>
              <a href="https://financeai.in/subscription" style="display:inline-block;background:#3b82f6;color:white;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:bold;margin-top:8px">
                Upgrade to Pro — ₹499/month
              </a>
            </div>
            """.formatted(user.getEmail(), daysRemaining, daysRemaining == 1 ? "" : "s");

        sendEmail(user.getEmail(), subject, html);
    }

    // ── Team Invite ───────────────────────────────────────────────────────────
    @Async
    public void sendTeamInvite(String toEmail, String companyName, String inviteUrl) {
        if (!mailEnabled) return;
        String subject = "You've been invited to " + companyName + " on FinanceAI";
        String html = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;background:#0f172a;color:#e2e8f0;padding:32px;border-radius:12px">
              <h2 style="color:#3b82f6;margin-top:0">👥 Team Invitation</h2>
              <p>You've been invited to access <strong>%s</strong>'s financial dashboard on FinanceAI.</p>
              <a href="%s" style="display:inline-block;background:#3b82f6;color:white;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:bold;margin-top:16px">
                Accept Invitation
              </a>
              <p style="color:#64748b;font-size:12px;margin-top:16px">This invitation expires in 72 hours.</p>
            </div>
            """.formatted(companyName, inviteUrl);

        sendEmail(toEmail, subject, html);
    }

    // ── Health Score Monthly Report ───────────────────────────────────────────
    @Async
    public void sendMonthlyHealthScore(User user, int score, String month, String recommendations) {
        if (!mailEnabled) return;
        UserEmailPrefs prefs = prefsRepo.findByUserId(user.getId()).orElse(null);
        if (prefs != null && !prefs.isWeeklySummary()) return;

        String color = score >= 70 ? "#10b981" : score >= 40 ? "#f59e0b" : "#ef4444";
        String subject = "📊 Your " + month + " Financial Health Score — FinanceAI";
        String html = """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;background:#0f172a;color:#e2e8f0;padding:32px;border-radius:12px">
              <h2 style="color:#3b82f6;margin-top:0">📊 Monthly Financial Health Report</h2>
              <p>Hi %s, here is your %s score:</p>
              <div style="text-align:center;margin:24px 0">
                <div style="font-size:72px;font-weight:bold;color:%s">%d</div>
                <div style="color:#94a3b8">out of 100</div>
              </div>
              <div style="background:#1e293b;padding:16px;border-radius:8px">
                <strong>AI Recommendations:</strong>
                <p style="color:#94a3b8;white-space:pre-line">%s</p>
              </div>
              <a href="https://financeai.in/dashboard" style="display:inline-block;background:#3b82f6;color:white;padding:12px 24px;border-radius:8px;text-decoration:none;font-weight:bold;margin-top:16px">
                View Full Dashboard
              </a>
            </div>
            """.formatted(user.getEmail(), month, color, score, recommendations);

        sendEmail(user.getEmail(), subject, html);
    }

    // ── Internal helper ───────────────────────────────────────────────────────
    private void sendEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(msg);
            log.info("Email sent to {} subject={}", to, subject);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
