package com.docflow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {
    private final SecurityStateStore state;
    private final ClientIpResolver clientIpResolver;
    private final List<ClientIpResolver.NetworkRule> denyRules;
    private final int generalLimit;
    private final int authLimit;
    private final int uploadLimit;
    private final int shareLimit;

    public ApiRateLimitFilter(
            SecurityStateStore state,
            ClientIpResolver clientIpResolver,
            @Value("${app.security.ip-deny-list:}") String denyList,
            @Value("${app.security.rate-limit.general-per-minute:180}") int generalLimit,
            @Value("${app.security.rate-limit.auth-per-minute:30}") int authLimit,
            @Value("${app.security.rate-limit.upload-per-minute:30}") int uploadLimit,
            @Value("${app.security.rate-limit.share-per-minute:60}") int shareLimit) {
        this.state = state;
        this.clientIpResolver = clientIpResolver;
        this.denyRules = ClientIpResolver.parseRules(denyList);
        this.generalLimit = Math.max(10, generalLimit);
        this.authLimit = Math.max(5, authLimit);
        this.uploadLimit = Math.max(5, uploadLimit);
        this.shareLimit = Math.max(5, shareLimit);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || !(path.startsWith("/api/") || path.startsWith("/ws/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = clientIpResolver.resolve(request);
        if (ClientIpResolver.matches(ip, denyRules)) {
            writeError(response, 403, "当前网络地址已被禁止访问");
            return;
        }
        int limit = limitFor(request);
        long minute = Instant.now().getEpochSecond() / 60L;
        long count = state.increment("rate:" + category(request) + ":" + digest(ip) + ":" + minute,
                Duration.ofSeconds(70));
        response.setHeader("X-RateLimit-Limit", Integer.toString(limit));
        response.setHeader("X-RateLimit-Remaining", Long.toString(Math.max(0L, limit - count)));
        if (count > limit) {
            response.setHeader("Retry-After", "60");
            writeError(response, 429, "请求过于频繁，请稍后再试");
            return;
        }
        chain.doFilter(request, response);
    }

    private int limitFor(HttpServletRequest request) {
        return switch (category(request)) {
            case "auth" -> authLimit;
            case "upload" -> uploadLimit;
            case "share" -> shareLimit;
            default -> generalLimit;
        };
    }

    private String category(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/api/v1/auth/")) return "auth";
        if (path.startsWith("/api/v1/share/") || path.startsWith("/api/v1/public-files")) return "share";
        if (path.contains("/upload") || path.contains("/attachments") || path.equals("/api/v1/files/upload")
                || path.equals("/api/v1/feedback")) return "upload";
        return "general";
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 24);
        } catch (Exception exception) {
            return "unknown";
        }
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":" + status + ",\"message\":\"" + message + "\",\"data\":null}");
    }
}
