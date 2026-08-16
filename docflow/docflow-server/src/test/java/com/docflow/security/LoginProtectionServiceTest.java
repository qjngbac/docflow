package com.docflow.security;

import com.docflow.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LoginProtectionServiceTest {
    private LoginProtectionService service;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        SecurityStateStore state = new SecurityStateStore(provider, false);
        service = new LoginProtectionService(state, 3, 5, 20, 15, 15, 30, 5);
    }

    @Test
    void requiresOneTimeCaptchaAfterRepeatedFailures() {
        for (int index = 0; index < 3; index++) service.recordFailure("alice", "10.0.0.8");
        assertThat(service.status("alice", "10.0.0.8").captchaRequired()).isTrue();

        LoginProtectionService.CaptchaChallenge challenge = service.createCaptcha("alice", "10.0.0.8");
        String svg = new String(Base64.getDecoder().decode(
                challenge.imageDataUrl().substring(challenge.imageDataUrl().indexOf(',') + 1)), StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile(">(\\w{5})</text>").matcher(svg);
        assertThat(matcher.find()).isTrue();
        String answer = matcher.group(1);

        service.checkBeforeLogin("alice", "10.0.0.8", challenge.captchaId(), answer);
        assertThatThrownBy(() -> service.checkBeforeLogin("alice", "10.0.0.8", challenge.captchaId(), answer))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("验证码");
    }

    @Test
    void temporarilyLocksAccountAtConfiguredThreshold() {
        for (int index = 0; index < 5; index++) service.recordFailure("alice", "10.0.0.9");

        assertThat(service.status("alice", "10.0.0.9").retryAfterSeconds()).isPositive();
        assertThatThrownBy(() -> service.checkBeforeLogin("alice", "10.0.0.9", null, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("登录失败次数过多");
    }

    @Test
    void productionFailClosedModeRejectsRequestsWithoutSharedSecurityState() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        SecurityStateStore state = new SecurityStateStore(provider, true);

        assertThatThrownBy(() -> state.increment("rate:test", Duration.ofMinutes(1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("安全状态服务");
    }
}
