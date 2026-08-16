package com.docflow.websocket;

import com.docflow.security.JwtUtil;
import com.docflow.service.PermissionService;
import com.docflow.service.AccountSecurityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PermissionService permissionService;
    @Autowired
    private AccountSecurityService accountSecurityService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        try {
            String token = resolveToken(request);
            if (!StringUtils.hasText(token) || !jwtUtil.validateToken(token)) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }

            Long docId = extractDocId(request);
            Long userId = jwtUtil.getUserId(token);
            if (!accountSecurityService.validateAccessSession(userId, jwtUtil.getTokenId(token))) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            permissionService.requireReadable(docId, userId);
            attributes.put("docId", docId);
            attributes.put("userId", userId);
            attributes.put("username", jwtUtil.getUsername(token));
            return true;
        } catch (Exception e) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private String resolveToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(authorization) && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("token");
    }

    private Long extractDocId(ServerHttpRequest request) {
        String path = request.getURI().getPath();
        return Long.valueOf(path.substring(path.lastIndexOf('/') + 1));
    }
}
