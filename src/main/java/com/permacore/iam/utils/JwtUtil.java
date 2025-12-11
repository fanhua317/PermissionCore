package com.permacore.iam.utils;

import cn.hutool.core.util.IdUtil;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT工具类
 * 支持Token生成、验证、刷新、强制失效
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成AccessToken
     * @param claims 自定义载荷
     * @return Token字符串
     */
    public String generateAccessToken(Map<String, Object> claims) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(String.valueOf(claims.get("userId")))
                .setId(IdUtil.fastUUID()) // JWT唯一标识(JTI)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration * 1000))
                .signWith(getSecretKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 生成RefreshToken
     * @param userId 用户ID
     * @return Token字符串
     */
    public String generateRefreshToken(Long userId) {
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setId(IdUtil.fastUUID())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshExpiration * 1000))
                .signWith(getSecretKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析Token
     * @param token Token字符串
     * @return Claims
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(getSecretKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            log.warn("Token已过期: {}", token);
            throw new RuntimeException("Token已过期，请重新登录");
        } catch (JwtException e) {
            log.error("Token解析失败: {}", token, e);
            throw new RuntimeException("Token无效");
        }
    }

    /**
     * 验证Token是否有效
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSecretKey())
                    .build()
                    .parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            log.warn("Token验证失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从Token中获取用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 从Token中获取JWT唯一标识(JTI)
     */
    public String getJtiFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.getId();
    }

    /**
     * 检查Token是否即将过期（剩余时间小于5分钟）
     */
    public boolean isTokenNearExpiration(String token) {
        try {
            Claims claims = parseToken(token);
            Date expiration = claims.getExpiration();
            long remainTime = expiration.getTime() - System.currentTimeMillis();
            return remainTime < 5 * 60 * 1000; // 5分钟
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 获取Token剩余有效期（秒）
     */
    public long getTokenRemainTime(String token) {
        try {
            Claims claims = parseToken(token);
            Date expiration = claims.getExpiration();
            return (expiration.getTime() - System.currentTimeMillis()) / 1000;
        } catch (Exception e) {
            return 0;
        }
    }
}