package com.docflow.security;

import com.docflow.service.AccountSecurityService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {
    private final JwtUtil jwtUtil = mock(JwtUtil.class);
    private final AccountSecurityService sessions = mock(AccountSecurityService.class);
    private final AuthCookieService cookies = mock(AuthCookieService.class);
    private final JwtAuthFilter filter = new JwtAuthFilter();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(filter, "jwtUtil", jwtUtil);
        ReflectionTestUtils.setField(filter, "accountSecurityService", sessions);
        ReflectionTestUtils.setField(filter, "authCookieService", cookies);
        when(jwtUtil.validateToken("token")).thenReturn(true);
        when(jwtUtil.getTokenType("token")).thenReturn("realtime");
    }

    @Test
    void realtimeTokenCannotAuthenticateOrdinaryApiRequests() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/docs");
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        verify(chain).doFilter(org.mockito.ArgumentMatchers.eq(request), org.mockito.ArgumentMatchers.any());
        verify(jwtUtil, never()).getUserId("token");
    }

    @Test
    void realtimeTokenCanAuthenticateCrdtAccessCheck() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/v1/docs/12/crdt/access");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(jwtUtil.getUserId("token")).thenReturn(3L);
        when(jwtUtil.getUsername("token")).thenReturn("alice");
        when(jwtUtil.getTokenId("token")).thenReturn("session-id");
        when(sessions.validateAccessSession(3L, "session-id")).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(sessions).validateAccessSession(3L, "session-id");
        verify(chain).doFilter(request, response);
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer token");
        return request;
    }
}
