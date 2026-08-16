package com.docflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import com.docflow.entity.UserSession;
import com.docflow.entity.User;
import com.docflow.entity.VerificationCode;
import com.docflow.mapper.UserSessionMapper;
import com.docflow.mapper.UserMapper;
import com.docflow.mapper.VerificationCodeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountSecurityService {
    private static final SecureRandom RANDOM = new SecureRandom();

    @Autowired private VerificationCodeMapper verificationCodeMapper;
    @Autowired private UserSessionMapper userSessionMapper;
    @Autowired private UserMapper userMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private VerificationMailService verificationMailService;
    @Autowired private VerificationCodeStore verificationCodeStore;
    @Value("${app.verification.debug-code:true}") private boolean debugCode;
    @Value("${app.verification.expire-minutes:10}") private int expireMinutes;
    @Value("${app.verification.cooldown-seconds:60}") private int cooldownSeconds;
    @Value("${app.verification.daily-target-limit:10}") private int dailyTargetLimit;
    @Value("${app.verification.daily-ip-limit:30}") private int dailyIpLimit;
    @Value("${app.environment:development}") private String environment;
    @Value("${app.auth.idle-timeout-minutes:30}") private int idleTimeoutMinutes;
    @Value("${app.auth.session-days:7}") private int sessionDays;
    @Value("${app.auth.remember-days:30}") private int rememberDays;
    @Value("${app.auth.touch-interval-seconds:60}") private int touchIntervalSeconds;

    public Map<String, String> issueCode(Long userId, String target, String purpose, String requestIp) {
        String normalizedTarget = target.toLowerCase();
        String normalizedIp = requestIp == null || requestIp.isBlank() ? "unknown" : requestIp;
        checkRateLimit(normalizedTarget, normalizedIp, purpose);
        VerificationCode code = new VerificationCode();
        String rawCode = String.format("%06d", RANDOM.nextInt(1_000_000));
        code.setUserId(userId);
        code.setTarget(normalizedTarget);
        code.setPurpose(purpose);
        code.setRequestIp(normalizedIp);
        code.setCodeHash(passwordEncoder.encode(rawCode));
        code.setExpiresAt(LocalDateTime.now().plusMinutes(expireMinutes));
        if (verificationMailService.isEnabled()) {
            verificationMailService.send(normalizedTarget, purpose, rawCode);
        } else if (!debugCode || "production".equalsIgnoreCase(environment)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Email delivery is disabled");
        }
        verificationCodeStore.replaceActiveCode(code);
        return !verificationMailService.isEnabled() && debugCode
                && !"production".equalsIgnoreCase(environment)
                ? Map.of("message", "Verification code created in development mode", "debugCode", rawCode)
                : Map.of("message", "Verification code sent");
    }

    private void checkRateLimit(String target, String requestIp, String purpose) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cooldownStart = now.minusSeconds(cooldownSeconds);
        Long recentTarget = verificationCodeMapper.selectCount(new LambdaQueryWrapper<VerificationCode>()
                .eq(VerificationCode::getTarget, target).eq(VerificationCode::getPurpose, purpose)
                .ge(VerificationCode::getCreatedAt, cooldownStart));
        Long recentIp = verificationCodeMapper.selectCount(new LambdaQueryWrapper<VerificationCode>()
                .eq(VerificationCode::getRequestIp, requestIp).ge(VerificationCode::getCreatedAt, cooldownStart));
        if (recentTarget > 0 || recentIp > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Please wait before requesting another verification code");
        }
        LocalDateTime dayStart = now.toLocalDate().atStartOfDay();
        Long targetToday = verificationCodeMapper.selectCount(new LambdaQueryWrapper<VerificationCode>()
                .eq(VerificationCode::getTarget, target).ge(VerificationCode::getCreatedAt, dayStart));
        Long ipToday = verificationCodeMapper.selectCount(new LambdaQueryWrapper<VerificationCode>()
                .eq(VerificationCode::getRequestIp, requestIp).ge(VerificationCode::getCreatedAt, dayStart));
        if (targetToday >= dailyTargetLimit || ipToday >= dailyIpLimit) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Verification code daily limit exceeded");
        }
    }

    @Transactional
    public void verify(Long userId, String target, String purpose, String rawCode) {
        VerificationCode code = verificationCodeMapper.selectOne(new LambdaQueryWrapper<VerificationCode>()
                .eq(userId != null, VerificationCode::getUserId, userId)
                .eq(VerificationCode::getTarget, target.toLowerCase())
                .eq(VerificationCode::getPurpose, purpose)
                .isNull(VerificationCode::getUsedAt)
                .orderByDesc(VerificationCode::getCreatedAt)
                .last("LIMIT 1"));
        if (code == null || code.getExpiresAt().isBefore(LocalDateTime.now())
                || !passwordEncoder.matches(rawCode, code.getCodeHash())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid or expired verification code");
        }
        code.setUsedAt(LocalDateTime.now());
        verificationCodeMapper.updateById(code);
    }

    @Transactional
    public SessionIssue recordSession(Long userId, boolean rememberMe, String userAgent, String ipAddress) {
        String tokenId = UUID.randomUUID().toString();
        String refreshToken = newRefreshToken();
        LocalDateTime now = LocalDateTime.now();
        UserSession session = new UserSession();
        session.setUserId(userId);
        session.setTokenId(tokenId);
        session.setDeviceName(describeDevice(userAgent));
        session.setIpAddress(ipAddress);
        session.setLastActiveAt(now);
        session.setRefreshTokenHash(hashToken(refreshToken));
        session.setExpiresAt(now.plusDays(rememberMe ? rememberDays : sessionDays));
        session.setRememberMe(rememberMe ? 1 : 0);
        userSessionMapper.insert(session);
        return new SessionIssue(session, refreshToken);
    }

    @Transactional
    public SessionIssue refreshSession(String refreshToken, String userAgent, String ipAddress) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Refresh token is required");
        }
        UserSession session = userSessionMapper.selectOne(new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getRefreshTokenHash, hashToken(refreshToken))
                .last("LIMIT 1 FOR UPDATE"));
        requireActiveSession(session);
        if (!isUserEnabled(session.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "账号已被封禁，请联系管理员");
        }
        String rotatedToken = newRefreshToken();
        session.setRefreshTokenHash(hashToken(rotatedToken));
        session.setLastActiveAt(LocalDateTime.now());
        session.setIpAddress(ipAddress);
        session.setDeviceName(describeDevice(userAgent));
        userSessionMapper.updateById(session);
        return new SessionIssue(session, rotatedToken);
    }

    public boolean validateAccessSession(Long userId, String tokenId) {
        if (userId == null || tokenId == null || tokenId.isBlank()) return false;
        UserSession session = userSessionMapper.selectOne(new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getUserId, userId)
                .eq(UserSession::getTokenId, tokenId)
                .last("LIMIT 1"));
        if (!isActive(session) || !isUserEnabled(userId)) return false;
        LocalDateTime now = LocalDateTime.now();
        if (session.getLastActiveAt() == null
                || session.getLastActiveAt().isBefore(now.minusSeconds(Math.max(10, touchIntervalSeconds)))) {
            userSessionMapper.update(null, new LambdaUpdateWrapper<UserSession>()
                    .eq(UserSession::getId, session.getId())
                    .isNull(UserSession::getRevokedAt)
                    .set(UserSession::getLastActiveAt, now));
        }
        return true;
    }

    public List<UserSession> listSessions(Long userId, String currentTokenId) {
        LocalDateTime idleCutoff = LocalDateTime.now().minusMinutes(idleTimeoutMinutes);
        return userSessionMapper.selectList(new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getUserId, userId)
                .isNull(UserSession::getRevokedAt)
                .orderByDesc(UserSession::getLastActiveAt)
                .last("LIMIT 20")).stream()
                .filter(session -> session.getLastActiveAt() != null && !session.getLastActiveAt().isBefore(idleCutoff))
                .filter(session -> session.getExpiresAt() == null || session.getExpiresAt().isAfter(LocalDateTime.now()))
                .peek(session -> session.setCurrent(session.getTokenId().equals(currentTokenId)))
                .toList();
    }

    @Transactional
    public boolean revokeSession(Long userId, Long sessionId, String currentTokenId) {
        UserSession session = userSessionMapper.selectOne(new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getId, sessionId).eq(UserSession::getUserId, userId).last("LIMIT 1"));
        if (session == null) throw new BusinessException(ErrorCode.NOT_FOUND, "Login session not found");
        if (session.getRevokedAt() == null) {
            session.setRevokedAt(LocalDateTime.now());
            session.setRefreshTokenHash(null);
            userSessionMapper.updateById(session);
        }
        return session.getTokenId().equals(currentTokenId);
    }

    public int revokeOtherSessions(Long userId, String currentTokenId) {
        return userSessionMapper.update(null, new LambdaUpdateWrapper<UserSession>()
                .eq(UserSession::getUserId, userId)
                .ne(currentTokenId != null, UserSession::getTokenId, currentTokenId)
                .isNull(UserSession::getRevokedAt)
                .set(UserSession::getRevokedAt, LocalDateTime.now())
                .set(UserSession::getRefreshTokenHash, null));
    }

    public int revokeAllSessions(Long userId, String exceptTokenId) {
        return userSessionMapper.update(null, new LambdaUpdateWrapper<UserSession>()
                .eq(UserSession::getUserId, userId)
                .ne(exceptTokenId != null, UserSession::getTokenId, exceptTokenId)
                .isNull(UserSession::getRevokedAt)
                .set(UserSession::getRevokedAt, LocalDateTime.now())
                .set(UserSession::getRefreshTokenHash, null));
    }

    public void revokeByRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return;
        userSessionMapper.update(null, new LambdaUpdateWrapper<UserSession>()
                .eq(UserSession::getRefreshTokenHash, hashToken(refreshToken))
                .isNull(UserSession::getRevokedAt)
                .set(UserSession::getRevokedAt, LocalDateTime.now())
                .set(UserSession::getRefreshTokenHash, null));
    }

    public long getIdleTimeoutSeconds() {
        return idleTimeoutMinutes * 60L;
    }

    private void requireActiveSession(UserSession session) {
        if (!isActive(session)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Login session has expired");
        }
    }

    private boolean isActive(UserSession session) {
        if (session == null || session.getRevokedAt() != null) return false;
        LocalDateTime now = LocalDateTime.now();
        if (session.getExpiresAt() != null && !session.getExpiresAt().isAfter(now)) return false;
        return session.getLastActiveAt() != null
                && session.getLastActiveAt().isAfter(now.minusMinutes(idleTimeoutMinutes));
    }

    private boolean isUserEnabled(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) return false;
        if (Integer.valueOf(1).equals(user.getStatus())) return true;
        if (user.getBanExpiresAt() == null || user.getBanExpiresAt().isAfter(LocalDateTime.now())) return false;
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .set(User::getStatus, 1)
                .set(User::getBanReason, null)
                .set(User::getBannedAt, null)
                .set(User::getBanExpiresAt, null));
        return true;
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record SessionIssue(UserSession session, String refreshToken) {}

    private String describeDevice(String userAgent) {
        if (userAgent == null) return "Unknown device";
        String browser = userAgent.contains("Edg/") ? "Edge" : userAgent.contains("Chrome/") ? "Chrome"
                : userAgent.contains("Firefox/") ? "Firefox" : userAgent.contains("Safari/") ? "Safari" : "Browser";
        String system = userAgent.contains("Windows") ? "Windows" : userAgent.contains("Android") ? "Android"
                : userAgent.contains("iPhone") || userAgent.contains("iPad") ? "iOS" : userAgent.contains("Mac OS") ? "macOS" : "Unknown OS";
        return browser + " on " + system;
    }
}
