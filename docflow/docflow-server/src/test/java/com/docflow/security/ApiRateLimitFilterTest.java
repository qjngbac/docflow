package com.docflow.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApiRateLimitFilterTest {
    @Test
    void rejectsRequestsThatExceedThePerIpLimit() throws Exception {
        SecurityStateStore state = mock(SecurityStateStore.class);
        when(state.increment(anyString(), any(Duration.class))).thenReturn(11L);
        ApiRateLimitFilter filter = new ApiRateLimitFilter(
                state, new ClientIpResolver("127.0.0.1/32"), "", 10, 5, 5, 5);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/docs");
        request.setRemoteAddr("203.0.113.8");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("请求过于频繁");
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void rejectsConfiguredIpRangesBeforeCallingApplication() throws Exception {
        SecurityStateStore state = mock(SecurityStateStore.class);
        ApiRateLimitFilter filter = new ApiRateLimitFilter(
                state, new ClientIpResolver("127.0.0.1/32"), "203.0.113.0/24", 10, 5, 5, 5);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/docs");
        request.setRemoteAddr("203.0.113.9");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("禁止访问");
        verify(state, never()).increment(anyString(), any(Duration.class));
    }
}
