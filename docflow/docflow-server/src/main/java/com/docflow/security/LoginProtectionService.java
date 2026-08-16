package com.docflow.security;

import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class LoginProtectionService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] CAPTCHA_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

    private final SecurityStateStore state;
    private final int captchaAfterFailures;
    private final int accountMaxFailures;
    private final int ipMaxFailures;
    private final Duration observationWindow;
    private final Duration accountLockDuration;
    private final Duration ipLockDuration;
    private final Duration captchaDuration;

    public LoginProtectionService(
            SecurityStateStore state,
            @Value("${app.security.login.captcha-after-failures:3}") int captchaAfterFailures,
            @Value("${app.security.login.account-max-failures:5}") int accountMaxFailures,
            @Value("${app.security.login.ip-max-failures:20}") int ipMaxFailures,
            @Value("${app.security.login.observation-minutes:15}") int observationMinutes,
            @Value("${app.security.login.account-lock-minutes:15}") int accountLockMinutes,
            @Value("${app.security.login.ip-lock-minutes:30}") int ipLockMinutes,
            @Value("${app.security.login.captcha-expire-minutes:5}") int captchaExpireMinutes) {
        this.state = state;
        this.captchaAfterFailures = Math.max(1, captchaAfterFailures);
        this.accountMaxFailures = Math.max(this.captchaAfterFailures + 1, accountMaxFailures);
        this.ipMaxFailures = Math.max(this.accountMaxFailures, ipMaxFailures);
        this.observationWindow = Duration.ofMinutes(Math.max(1, observationMinutes));
        this.accountLockDuration = Duration.ofMinutes(Math.max(1, accountLockMinutes));
        this.ipLockDuration = Duration.ofMinutes(Math.max(1, ipLockMinutes));
        this.captchaDuration = Duration.ofMinutes(Math.max(1, captchaExpireMinutes));
    }

    public LoginSecurityStatus status(String username, String ip) {
        String account = digest(normalizeUsername(username));
        String address = digest(normalizeIp(ip));
        long retry = Math.max(state.ttlSeconds(accountLockKey(account)), state.ttlSeconds(ipLockKey(address)));
        boolean captcha = retry == 0 && (state.getLong(accountFailureKey(account)) >= captchaAfterFailures
                || state.getLong(ipFailureKey(address)) >= captchaAfterFailures);
        return new LoginSecurityStatus(captcha, retry);
    }

    public CaptchaChallenge createCaptcha(String username, String ip) {
        String answer = randomCaptcha();
        String id = UUID.randomUUID().toString();
        String binding = digest(normalizeUsername(username)) + ":" + digest(normalizeIp(ip)) + ":" + digest(answer);
        state.put(captchaKey(id), binding, captchaDuration);
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='160' height='48' viewBox='0 0 160 48'>"
                + "<rect width='160' height='48' rx='4' fill='#eef4ff'/><path d='M8 35L152 12M15 10L145 38' stroke='#9db8e8' stroke-width='1'/>"
                + "<text x='80' y='32' text-anchor='middle' font-family='Consolas,monospace' font-size='25' font-weight='700' letter-spacing='5' fill='#17335f'>"
                + answer + "</text></svg>";
        String image = "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));
        return new CaptchaChallenge(id, image, captchaDuration.toSeconds());
    }

    public void checkBeforeLogin(String username, String ip, String captchaId, String captchaCode) {
        LoginSecurityStatus status = status(username, ip);
        if (status.retryAfterSeconds() > 0) {
            long minutes = Math.max(1L, (status.retryAfterSeconds() + 59L) / 60L);
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "登录失败次数过多，请在 " + minutes + " 分钟后重试");
        }
        if (!status.captchaRequired()) return;
        if (captchaId == null || captchaId.isBlank() || captchaCode == null || captchaCode.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请输入登录验证码");
        }
        String expected = state.get(captchaKey(captchaId));
        state.delete(captchaKey(captchaId));
        String actual = digest(normalizeUsername(username)) + ":" + digest(normalizeIp(ip)) + ":"
                + digest(captchaCode.trim().toUpperCase(Locale.ROOT));
        if (expected == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "登录验证码不正确或已过期");
        }
    }

    public long recordFailure(String username, String ip) {
        String account = digest(normalizeUsername(username));
        String address = digest(normalizeIp(ip));
        long accountFailures = state.increment(accountFailureKey(account), observationWindow);
        long ipFailures = state.increment(ipFailureKey(address), observationWindow);
        if (accountFailures >= accountMaxFailures) state.put(accountLockKey(account), "1", accountLockDuration);
        if (ipFailures >= ipMaxFailures) state.put(ipLockKey(address), "1", ipLockDuration);
        return status(username, ip).retryAfterSeconds();
    }

    public void recordSuccess(String username) {
        String account = digest(normalizeUsername(username));
        state.delete(accountFailureKey(account));
        state.delete(accountLockKey(account));
    }

    private String randomCaptcha() {
        StringBuilder value = new StringBuilder(5);
        for (int index = 0; index < 5; index++) value.append(CAPTCHA_CHARS[RANDOM.nextInt(CAPTCHA_CHARS.length)]);
        return value.toString();
    }

    private String normalizeUsername(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeIp(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String accountFailureKey(String account) { return "login:failure:account:" + account; }
    private String ipFailureKey(String ip) { return "login:failure:ip:" + ip; }
    private String accountLockKey(String account) { return "login:lock:account:" + account; }
    private String ipLockKey(String ip) { return "login:lock:ip:" + ip; }
    private String captchaKey(String id) { return "login:captcha:" + id; }

    public record LoginSecurityStatus(boolean captchaRequired, long retryAfterSeconds) {}
    public record CaptchaChallenge(String captchaId, String imageDataUrl, long expiresInSeconds) {}
}
