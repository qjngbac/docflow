package com.docflow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import com.docflow.service.AccountSecurityService;

import java.io.IOException;
import java.util.Collections;

/**
 * 从请求头或安全 Cookie 恢复用户上下文，并拒绝被撤销会话以及用途不匹配的令牌。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private AccountSecurityService accountSecurityService;
    @Autowired
    private AuthCookieService authCookieService;

    /**
     * 文档导出等接口返回 Callable，Spring MVC 会在 ASYNC 派发时再次经过安全过滤器链。
     * OncePerRequestFilter 默认跳过 ASYNC 派发，而 SecurityContextHolderFilter 不会把上下文写回存储，
     * 结果是异步派发时安全上下文为空、anyRequest().authenticated() 判定失败，
     * 表现为「带着有效令牌访问导出接口仍然 401」。这里让本过滤器在 ASYNC 派发时也执行一次令牌校验。
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String token = resolveToken(request);
            if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
                if ("realtime".equals(jwtUtil.getTokenType(token)) && !isRealtimeAccessRequest(request)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                Long userId = jwtUtil.getUserId(token);
                String username = jwtUtil.getUsername(token);
                String tokenId = jwtUtil.getTokenId(token);
                if (!accountSecurityService.validateAccessSession(userId, tokenId)) {
                    filterChain.doFilter(request, response);
                    return;
                }
                UserContext.set(userId, username, tokenId);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return authCookieService.readAccessToken(request);
    }

    private boolean isRealtimeAccessRequest(HttpServletRequest request) {
        return "GET".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().matches("/api/v1/docs/\\d+/crdt/access");
    }
}
