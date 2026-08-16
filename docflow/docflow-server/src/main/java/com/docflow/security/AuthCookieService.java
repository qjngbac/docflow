package com.docflow.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;

@Component
public class AuthCookieService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final boolean enabled;
    private final boolean secure;
    private final String sameSite;
    private final String accessCookieName;
    private final String refreshCookieName;
    private final String csrfCookieName;

    public AuthCookieService(
            @Value("${app.auth.cookie.enabled:false}") boolean enabled,
            @Value("${app.auth.cookie.secure:false}") boolean secure,
            @Value("${app.auth.cookie.same-site:Lax}") String sameSite,
            @Value("${app.auth.cookie.access-name:DOCFLOW_ACCESS}") String accessCookieName,
            @Value("${app.auth.cookie.refresh-name:DOCFLOW_REFRESH}") String refreshCookieName,
            @Value("${app.auth.cookie.csrf-name:XSRF-TOKEN}") String csrfCookieName) {
        this.enabled = enabled;
        this.secure = secure;
        this.sameSite = normalizeSameSite(sameSite);
        this.accessCookieName = accessCookieName;
        this.refreshCookieName = refreshCookieName;
        this.csrfCookieName = csrfCookieName;
    }

    public boolean isEnabled() { return enabled; }

    public String readAccessToken(HttpServletRequest request) {
        return readCookie(request, accessCookieName);
    }

    public String readRefreshToken(HttpServletRequest request) {
        return readCookie(request, refreshCookieName);
    }

    public String readCsrfToken(HttpServletRequest request) {
        return readCookie(request, csrfCookieName);
    }

    public void write(HttpServletResponse response, String accessToken, long accessSeconds,
                      String refreshToken, LocalDateTime sessionExpiresAt, boolean persistent) {
        if (!enabled) return;
        long refreshSeconds = Math.max(60L, sessionExpiresAt.atZone(ZoneId.systemDefault()).toEpochSecond()
                - java.time.Instant.now().getEpochSecond());
        add(response, accessCookieName, accessToken, "/", persistent ? accessSeconds : null, true);
        add(response, refreshCookieName, refreshToken, "/api/v1/auth", persistent ? refreshSeconds : null, true);
        byte[] csrfBytes = new byte[32];
        RANDOM.nextBytes(csrfBytes);
        add(response, csrfCookieName, Base64.getUrlEncoder().withoutPadding().encodeToString(csrfBytes),
                "/", persistent ? refreshSeconds : null, false);
    }

    public void clear(HttpServletResponse response) {
        if (!enabled) return;
        add(response, accessCookieName, "", "/", 0L, true);
        add(response, refreshCookieName, "", "/api/v1/auth", 0L, true);
        add(response, csrfCookieName, "", "/", 0L, false);
    }

    private String readCookie(HttpServletRequest request, String name) {
        if (!enabled || request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private void add(HttpServletResponse response, String name, String value, String path,
                     Long maxAgeSeconds, boolean httpOnly) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(secure)
                .sameSite(sameSite)
                .path(path);
        if (maxAgeSeconds != null) builder.maxAge(Duration.ofSeconds(maxAgeSeconds));
        ResponseCookie cookie = builder.build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String normalizeSameSite(String value) {
        if (value == null) return "Lax";
        return switch (value.trim().toLowerCase()) {
            case "strict" -> "Strict";
            case "none" -> "None";
            default -> "Lax";
        };
    }
}
