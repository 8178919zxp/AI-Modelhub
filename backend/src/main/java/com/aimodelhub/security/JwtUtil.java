package com.aimodelhub.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;

    /**
     * 密钥校验（示例密钥 / 弱口令的拒绝）统一放在
     * {@code ProductionSafetyValidator} 中，所有凭据在一处检查。
     * 本类只负责令牌的签发与解析。
     */
    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                   @Value("${app.jwt.expiration-ms}") long expirationMs) {
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMs = expirationMs;
    }

    /**
     * 签发 JWT，并写入唯一的 {@code jti}（JWT ID）声明。
     * {@link TokenBlacklistService} 依靠 {@code jti} 精确吊销某个令牌（例如退出登录时）。
     */
    public String generateToken(Long userId, String username, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .id(UUID.randomUUID().toString())    // 写入 jti 声明，供黑名单吊销使用
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /** 解析并验签 JWT，返回其中的声明；签名不合法或已过期时抛出异常。 */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 校验令牌是否有效（签名正确且未过期），不抛异常。 */
    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long getUserId(String token) {
        return Long.valueOf(parseToken(token).getSubject());
    }

    public String getUsername(String token) {
        return parseToken(token).get("username", String.class);
    }

    public String getRole(String token) {
        return parseToken(token).get("role", String.class);
    }

    /**
     * 取出令牌中的 JWT ID（jti 声明）。
     * 老版本令牌没有 jti 时返回 null（向后兼容）。
     */
    public String getJti(String token) {
        try {
            return parseToken(token).getId();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 取出令牌的签发时间（JWT 规范要求精确到秒）。
     * 解析失败时返回 null。
     */
    public Date getIssuedAt(String token) {
        try {
            return parseToken(token).getIssuedAt();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 取出令牌的过期时间。
     * 解析失败时返回 null。
     */
    public Date getExpiration(String token) {
        try {
            return parseToken(token).getExpiration();
        } catch (Exception e) {
            return null;
        }
    }
}
