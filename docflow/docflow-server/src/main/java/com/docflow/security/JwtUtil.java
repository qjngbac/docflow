package com.docflow.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 Token
     */
    public String generateToken(Long userId, String username) {
        return generateToken(userId, username, java.util.UUID.randomUUID().toString());
    }

    public String generateToken(Long userId, String username, String tokenId) {
        return generateToken(userId, username, tokenId, expiration, "access");
    }

    public String generateRealtimeToken(Long userId, String username, String tokenId) {
        return generateToken(userId, username, tokenId, 120_000L, "realtime");
    }

    private String generateToken(Long userId, String username, String tokenId, long lifetime, String tokenType) {
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("tokenType", tokenType)
                .id(tokenId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + lifetime))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 解析 Token
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Token 中获取用户ID
     */
    public Long getUserId(String token) {
        Claims claims = parseToken(token);
        return claims.get("userId", Long.class);
    }

    /**
     * 从 Token 中获取用户名
     */
    public String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims.getSubject();
    }

    public String getTokenId(String token) {
        return parseToken(token).getId();
    }

    public String getTokenType(String token) {
        String type = parseToken(token).get("tokenType", String.class);
        return type == null || type.isBlank() ? "access" : type;
    }

    public long getExpirationMillis() {
        return expiration;
    }

    /**
     * 校验 Token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            return !claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}
