package com.crossborder.tenant.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT工具类
 */
@Component
public class JwtTokenProvider {

    /**
     * 🔴 **密钥必须由环境变量注入，不能写默认值**（2026-11-12 修）
     *    原来这里是 `@Value("${jwt.secret:OmniTradeERP Secret Key For SaaS 2026 Multi-Tenant Platform}")`
     *    —— **默认值就在源码里** → 任何拿到仓库的人都能签出租户管理员 token。
     *    📌 注意：这里**光改 application.yml 不够** —— @Value 的默认值在
     *    「配置项缺失」时会**顶上来** → 所以 Java 侧的默认值也必须一起去掉。
     *    📌 部署侧注入见 k8s/extended-services.yaml（secretKeyRef: erp-jwt-secret / jwt-secret）
     */
    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成JWT Token（包含租户ID）
     */
    public String generateToken(String userId, String tenantId, String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpiration);

        return Jwts.builder()
                .subject(userId)
                .claim("tenantId", tenantId)
                .claim("username", username)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 从Token获取用户ID
     */
    public String getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    /**
     * 从Token获取租户ID
     */
    public String getTenantIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("tenantId", String.class);
    }

    /**
     * 验证Token
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 从Token获取用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("username", String.class);
    }
}