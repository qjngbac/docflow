package com.docflow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

@Component
public class CookieCsrfFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private final AuthCookieService cookies;

    public CookieCsrfFilter(AuthCookieService cookies) {
        this.cookies = cookies;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!cookies.isEnabled() || SAFE_METHODS.contains(request.getMethod().toUpperCase())) return true;
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) return true;
        String path = request.getRequestURI();
        return path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/register")
                || path.equals("/api/v1/auth/refresh") || path.startsWith("/api/v1/auth/password-reset/")
                || path.startsWith("/api/v1/share/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!StringUtils.hasText(cookies.readAccessToken(request))) {
            chain.doFilter(request, response);
            return;
        }
        String cookieToken = cookies.readCsrfToken(request);
        String headerToken = request.getHeader("X-XSRF-TOKEN");
        if (!StringUtils.hasText(cookieToken) || !StringUtils.hasText(headerToken)
                || !MessageDigest.isEqual(cookieToken.getBytes(StandardCharsets.UTF_8),
                headerToken.getBytes(StandardCharsets.UTF_8))) {
            response.setStatus(403);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":403,\"message\":\"安全校验失败，请刷新页面后重试\",\"data\":null}");
            return;
        }
        chain.doFilter(request, response);
    }
}
