package com.permacore.iam.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JWT 生成与校验工具。Access Token 与 Refresh Token 使用显式类型声明，
 * 并共享一个可撤销的 sessionId。
 */
@Component
public class JwtUtil {

    public static final String CLAIM_TOKEN_TYPE = "tokenType";
    public static final String CLAIM_SESSION_ID = "sessionId";
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    private static final int MIN_SECRET_BYTES = 32;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    private volatile SecretKey secretKey;

    @PostConstruct
    public void initializeSecretKey() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET 未配置");
        }
        byte[] keyBytes = secret.startsWith("base64:")
                ? Decoders.BASE64.decode(secret.substring("base64:".length()))
                : secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET 至少需要 32 字节");
        }
        secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(Map<String, Object> claims, String sessionId) {
        return generateToken(claims, sessionId, TOKEN_TYPE_ACCESS, expiration);
    }

    public String generateRefreshToken(Map<String, Object> claims, String sessionId) {
        return generateToken(claims, sessionId, TOKEN_TYPE_REFRESH, refreshExpiration);
    }

    private String generateToken(Map<String, Object> claims, String sessionId, String tokenType, long ttlSeconds) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId 不能为空");
        }
        Object userId = claims == null ? null : claims.get("userId");
        if (userId == null) {
            throw new IllegalArgumentException("JWT claims 缺少 userId");
        }

        Map<String, Object> tokenClaims = claims == null ? new HashMap<>() : new HashMap<>(claims);
        tokenClaims.put(CLAIM_TOKEN_TYPE, tokenType);
        tokenClaims.put(CLAIM_SESSION_ID, sessionId);
        return Jwts.builder()
                .claims(tokenClaims)
                .subject(String.valueOf(userId))
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ttlSeconds * 1000))
                .signWith(getSecretKey(), Jwts.SIG.HS256)
                .compact();
    }

    public Claims parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token 不能为空");
        }
        try {
            return Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("Token 已过期");
            throw new IllegalArgumentException("Token 已过期，请重新登录", e);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token 校验失败: {}", e.getClass().getSimpleName());
            throw new IllegalArgumentException("Token 无效", e);
        }
    }

    public boolean isAccessToken(Claims claims) {
        return TOKEN_TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return TOKEN_TYPE_REFRESH.equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
    }

    public String getSessionId(Claims claims) {
        return claims.get(CLAIM_SESSION_ID, String.class);
    }

    public Long getUserIdFromToken(String token) {
        return Long.parseLong(parseToken(token).getSubject());
    }

    public String getUsernameFromToken(String token) {
        Object username = parseToken(token).get("username");
        return username == null ? null : username.toString();
    }

    public long getTokenRemainTime(String token) {
        try {
            long remainMillis = parseToken(token).getExpiration().getTime() - System.currentTimeMillis();
            return Math.max(0, remainMillis / 1000);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private SecretKey getSecretKey() {
        if (secretKey == null) {
            initializeSecretKey();
        }
        return secretKey;
    }
}
