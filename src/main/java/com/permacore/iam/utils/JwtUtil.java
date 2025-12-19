package com.permacore.iam.utils;

import cn.hutool.core.util.IdUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT工具类
 * 支持Token生成、验证、刷新、强制失效
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    @Value("${jwt.refresh-expiration}")
    private Long refreshExpiration;

    private SecretKey getSecretKey() {
        byte[] keyBytes = secret.length() % 4 == 0 ? Decoders.BASE64.decode(secret) : secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
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
     * 生成RefreshToken (带claims)
     * @param claims 自定义载荷
     * @return Token字符串
     */
    public String generateRefreshToken(Map<String, Object> claims) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(String.valueOf(claims.get("userId")))
                .setId(IdUtil.fastUUID())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshExpiration * 1000))
                .signWith(getSecretKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 从Token中获取版本号(JTI作为版本号)
     * @param token Token字符串
     * @return 版本号
     */
    public String getVersionFromToken(String token) {
        return parseToken(token).getId();
    }

    /**
     * 解析Token
     * @param token Token字符串
     * @return Claims
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("Token已过期: {}", token);
            throw new RuntimeException("Token已过期，请重新登录");
        } catch (JwtException e) {
            log.error("Token解析失败: {}", token, e);
            throw new RuntimeException("Token无效");
        } catch (Exception e) {
            log.error("解析Token失败: {}", token, e);
            throw new RuntimeException("Token解析错误");
        }
    }

    /**
     * 验证Token是否有效
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(token);
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
        return Long.parseLong(parseToken(token).getSubject());
    }

    /**
     * 从Token中获取用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        Object username = claims.get("username");
        return username != null ? username.toString() : null;
    }

    /**
     * 从Token中获取JWT唯一标识(JTI)
     */
    public String getJtiFromToken(String token) {
        return parseToken(token).getId();
    }

    /**
     * 检查Token是否即将过期（剩余时间小于5分钟）
     */
    public boolean isTokenNearExpiration(String token) {
        try {
            Date expiration = parseToken(token).getExpiration();
            return expiration.getTime() - System.currentTimeMillis() < 5 * 60 * 1000;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 获取Token剩余有效期（秒）
     */
    public long getTokenRemainTime(String token) {
        try {
            Date expiration = parseToken(token).getExpiration();
            return (expiration.getTime() - System.currentTimeMillis()) / 1000;
        } catch (Exception e) {
            return 0;
        }
    }
}