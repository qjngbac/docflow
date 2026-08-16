package com.docflow.service;

import com.docflow.common.BusinessException;
import com.docflow.entity.VerificationCode;
import com.docflow.entity.UserSession;
import com.docflow.mapper.UserSessionMapper;
import com.docflow.mapper.VerificationCodeMapper;
import com.docflow.mapper.UserMapper;
import com.docflow.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountSecurityServiceTest {
    @Mock private VerificationCodeMapper verificationCodeMapper;
    @Mock private UserSessionMapper userSessionMapper;
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private VerificationMailService verificationMailService;
    @Mock private VerificationCodeStore verificationCodeStore;
    @InjectMocks private AccountSecurityService service;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "expireMinutes", 10);
        ReflectionTestUtils.setField(service, "cooldownSeconds", 60);
        ReflectionTestUtils.setField(service, "dailyTargetLimit", 10);
        ReflectionTestUtils.setField(service, "dailyIpLimit", 30);
        ReflectionTestUtils.setField(service, "idleTimeoutMinutes", 30);
        ReflectionTestUtils.setField(service, "sessionDays", 7);
        ReflectionTestUtils.setField(service, "rememberDays", 30);
        ReflectionTestUtils.setField(service, "touchIntervalSeconds", 60);
        User enabledUser = new User();
        enabledUser.setId(2L);
        enabledUser.setStatus(1);
        org.mockito.Mockito.lenient().when(userMapper.selectById(2L)).thenReturn(enabledUser);
    }

    @Test
    void sendsMailBeforePersistingCode() {
        when(verificationCodeMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(verificationMailService.isEnabled()).thenReturn(true);

        service.issueCode(2L, "user@test.com", "EMAIL_CHANGE", "127.0.0.1");

        verify(verificationMailService).send(any(), any(), any());
        verify(verificationCodeStore).replaceActiveCode(any());
    }

    @Test
    void doesNotPersistCodeWhenMailDeliveryFails() {
        when(verificationCodeMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(verificationMailService.isEnabled()).thenReturn(true);
        doThrow(new BusinessException(com.docflow.common.ErrorCode.INTERNAL_ERROR, "Unable to send verification email"))
                .when(verificationMailService).send(any(), any(), any());

        assertThatThrownBy(() -> service.issueCode(2L, "user@test.com", "EMAIL_CHANGE", "127.0.0.1"))
                .isInstanceOf(BusinessException.class);
        verify(verificationCodeStore, never()).replaceActiveCode(any());
    }

    @Test
    void rejectsRequestsDuringCooldown() {
        when(verificationCodeMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.issueCode(2L, "user@test.com", "EMAIL_CHANGE", "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("wait");
        verify(verificationMailService, never()).send(any(), any(), any());
    }

    @Test
    void acceptsValidVerificationCodeOnce() {
        VerificationCode code = code(LocalDateTime.now().plusMinutes(5));
        when(verificationCodeMapper.selectOne(any())).thenReturn(code);
        when(passwordEncoder.matches("123456", "hash")).thenReturn(true);

        service.verify(2L, "user@test.com", "EMAIL_CHANGE", "123456");

        verify(verificationCodeMapper).updateById(code);
    }

    @Test
    void rejectsExpiredVerificationCode() {
        when(verificationCodeMapper.selectOne(any())).thenReturn(code(LocalDateTime.now().minusSeconds(1)));

        assertThatThrownBy(() -> service.verify(2L, "user@test.com", "EMAIL_CHANGE", "123456"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void createsHashedRefreshTokenAndRememberedExpiry() {
        AccountSecurityService.SessionIssue issue = service.recordSession(
                2L, true, "Mozilla/5.0 (Windows NT 10.0) Chrome/120", "127.0.0.1");

        assertThat(issue.refreshToken()).isNotBlank();
        assertThat(issue.session().getRefreshTokenHash()).hasSize(64).doesNotContain(issue.refreshToken());
        assertThat(issue.session().getRememberMe()).isEqualTo(1);
        assertThat(issue.session().getExpiresAt()).isAfter(LocalDateTime.now().plusDays(29));
        verify(userSessionMapper).insert(issue.session());
    }

    @Test
    void rotatesRefreshTokenOnEveryUse() {
        AccountSecurityService.SessionIssue login = service.recordSession(
                2L, false, "Mozilla/5.0 (Windows NT 10.0) Chrome/120", "127.0.0.1");
        String firstHash = login.session().getRefreshTokenHash();
        when(userSessionMapper.selectOne(any())).thenReturn(login.session());

        AccountSecurityService.SessionIssue refreshed = service.refreshSession(
                login.refreshToken(), "Mozilla/5.0 (Windows NT 10.0) Chrome/120", "127.0.0.1");

        assertThat(refreshed.refreshToken()).isNotEqualTo(login.refreshToken());
        assertThat(refreshed.session().getRefreshTokenHash()).isNotEqualTo(firstHash);
        verify(userSessionMapper).updateById(login.session());
    }

    @Test
    void rejectsRevokedAccessSession() {
        UserSession session = new UserSession();
        session.setUserId(2L);
        session.setTokenId("session-token");
        session.setLastActiveAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusDays(1));
        session.setRevokedAt(LocalDateTime.now());
        when(userSessionMapper.selectOne(any())).thenReturn(session);

        assertThat(service.validateAccessSession(2L, "session-token")).isFalse();
    }

    @Test
    void rejectsAccessSessionForBannedUser() {
        UserSession session = new UserSession();
        session.setUserId(2L);
        session.setTokenId("session-token");
        session.setLastActiveAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusDays(1));
        User banned = new User(); banned.setId(2L); banned.setStatus(0);
        when(userSessionMapper.selectOne(any())).thenReturn(session);
        when(userMapper.selectById(2L)).thenReturn(banned);

        assertThat(service.validateAccessSession(2L, "session-token")).isFalse();
    }

    private VerificationCode code(LocalDateTime expiresAt) {
        VerificationCode code = new VerificationCode();
        code.setId(1L); code.setUserId(2L); code.setTarget("user@test.com");
        code.setPurpose("EMAIL_CHANGE"); code.setCodeHash("hash"); code.setExpiresAt(expiresAt);
        return code;
    }
}
