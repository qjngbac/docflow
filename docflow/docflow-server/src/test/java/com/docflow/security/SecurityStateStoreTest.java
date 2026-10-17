package com.docflow.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityStateStoreTest {
    @Test void expiredFallbackKeysAreReclaimedWithoutBeingRequestedAgain() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        SecurityStateStore state = new SecurityStateStore(provider, false);
        state.put("rate:minute-1", "1", Duration.ofMillis(-1));
        state.put("active-lock", "1", Duration.ofMinutes(5));
        ReflectionTestUtils.invokeMethod(state, "removeExpiredLocalValues");
        assertThat((Map<?, ?>) ReflectionTestUtils.getField(state, "local")).hasSize(1);
        assertThat(state.get("active-lock")).isEqualTo("1");
    }
}
