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

    @Test
    void fileCookieAuthenticatesOnlyReadOnlyFileRequests() throws Exception {
        var realCookies = new AuthCookieService(false, true, "Lax", "ACCESS", "REFRESH", "CSRF");
        ReflectionTestUtils.setField(filter, "authCookieService", realCookies);
        when(jwtUtil.getTokenType("token")).thenReturn("access");
        when(jwtUtil.getUserId("token")).thenReturn(3L);
        when(jwtUtil.getUsername("token")).thenReturn("alice");
        when(jwtUtil.getTokenId("token")).thenReturn("session-id");
        when(sessions.validateAccessSession(3L, "session-id")).thenReturn(true);
        for (String[] route : new String[][]{{"GET", "/files/a.png"}, {"HEAD", "/files/a.png"},
                {"GET", "/api/v1/docs"}, {"POST", "/files/a.png"}}) {
            var request = new MockHttpServletRequest(route[0], route[1]);
            request.setCookies(new jakarta.servlet.http.Cookie("DOCFLOW_FILE_ACCESS", "token"));
            var identity = new java.util.concurrent.atomic.AtomicReference<Long>();
            filter.doFilter(request, new MockHttpServletResponse(), (req, res) ->
                    identity.set(UserContext.get() == null ? null : UserContext.get().getUserId()));
            org.assertj.core.api.Assertions.assertThat(identity.get())
                    .isEqualTo(route[1].startsWith("/files/") && !route[0].equals("POST") ? 3L : null);
        }
    }
}
