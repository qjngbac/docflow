package com.docflow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CookieSecurityTest {
    private final AuthCookieService cookies = new AuthCookieService(
            true, true, "Lax", "DOCFLOW_ACCESS", "DOCFLOW_REFRESH", "XSRF-TOKEN");

    @Test
    void rememberMeControlsWhetherAuthenticationCookiesPersist() {
        MockHttpServletResponse sessionResponse = new MockHttpServletResponse();
        cookies.write(sessionResponse, "access", 1800, "refresh", LocalDateTime.now().plusDays(7), false);
        assertThat(sessionResponse.getHeaders("Set-Cookie"))
                .allMatch(value -> value.contains("SameSite=Lax"))
                .noneMatch(value -> value.contains("Max-Age="));
        assertThat(sessionResponse.getHeaders("Set-Cookie"))
                .anyMatch(value -> value.startsWith("DOCFLOW_ACCESS=") && value.contains("HttpOnly") && value.contains("Secure"));

        MockHttpServletResponse persistentResponse = new MockHttpServletResponse();
        cookies.write(persistentResponse, "access", 1800, "refresh", LocalDateTime.now().plusDays(7), true);
        assertThat(persistentResponse.getHeaders("Set-Cookie"))
                .allMatch(value -> value.contains("Max-Age="));
    }

    @Test
    void cookieAuthenticatedMutationRequiresMatchingCsrfHeader() throws Exception {
        CookieCsrfFilter filter = new CookieCsrfFilter(cookies);
        MockHttpServletRequest rejected = requestWithAuthCookies();
        MockHttpServletResponse rejectedResponse = new MockHttpServletResponse();
        filter.doFilter(rejected, rejectedResponse, mock(FilterChain.class));
        assertThat(rejectedResponse.getStatus()).isEqualTo(403);

        MockHttpServletRequest accepted = requestWithAuthCookies();
        accepted.addHeader("X-XSRF-TOKEN", "csrf-value");
        MockHttpServletResponse acceptedResponse = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(accepted, acceptedResponse, chain);
        verify(chain).doFilter(accepted, acceptedResponse);
    }

    private MockHttpServletRequest requestWithAuthCookies() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/docs/1");
        request.setCookies(new Cookie("DOCFLOW_ACCESS", "access"), new Cookie("XSRF-TOKEN", "csrf-value"));
        return request;
    }
}
