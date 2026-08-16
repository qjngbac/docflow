package com.docflow.service;

import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
public class VerificationMailService {

    @Autowired(required = false) private JavaMailSender mailSender;
    @Value("${app.mail.enabled:false}") private boolean enabled;
    @Value("${app.mail.from:}") private String from;
    @Value("${app.verification.expire-minutes:10}") private int expireMinutes;

    public boolean isEnabled() {
        return enabled;
    }

    public void send(String target, String purpose, String code) {
        if (!enabled) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Email delivery is disabled");
        }
        if (mailSender == null || !StringUtils.hasText(from)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Email delivery is not configured");
        }
        String action = "PASSWORD_RESET".equals(purpose) ? "重置密码" : "修改邮箱";
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(target);
            helper.setSubject("云笺验证码 - " + action);
            helper.setText(html(action, code), true);
            mailSender.send(message);
        } catch (MailException | MessagingException e) {
            log.warn("Verification email delivery failed for {} ({})", mask(target), e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Unable to send verification email");
        }
    }

    private String html(String action, String code) {
        return """
                <!doctype html><html><body style="font-family:Arial,sans-serif;color:#1f2937">
                <div style="max-width:520px;margin:auto;padding:24px;border:1px solid #e5e7eb">
                  <h2 style="margin-top:0;color:#2563eb">云笺安全验证</h2>
                  <p>你正在进行%s操作，本次验证码为：</p>
                  <p style="font-size:30px;font-weight:700;letter-spacing:6px;color:#111827">%s</p>
                  <p>验证码在 %d 分钟内有效且只能使用一次。</p>
                  <p style="color:#6b7280;font-size:13px">如果不是你本人操作，请忽略此邮件，不要向任何人提供验证码。</p>
                </div></body></html>
                """.formatted(action, code, expireMinutes);
    }

    private String mask(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at <= 1) return "***";
        return email.charAt(0) + "***" + email.substring(at);
    }
}
