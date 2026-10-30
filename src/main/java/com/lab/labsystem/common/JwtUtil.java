package com.lab.labsystem.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID; // ⚠️ 新增导入

@Component
public class JwtUtil {

    // 保留静态字段与静态方法，调用方（JwtInterceptor / LoginController）零改动
    private static String secret;
    private static long expireMillis;
    private static Key key;

    @Value("${jwt.secret}")
    public void setSecret(String secret) {
        JwtUtil.secret = secret;
    }

    @Value("${jwt.expire-hours:24}")
    public void setExpireHours(long hours) {
        JwtUtil.expireMillis = hours * 60 * 60 * 1000;
    }

    @PostConstruct
    public void init() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("jwt.secret 长度必须 >= 32 字节（HS256 要求）");
        }
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public static String generateToken(String username, String role) {
        return Jwts.builder()
                .setId(UUID.randomUUID().toString()) // ⚠️ 核心新增：给每个 Token 发一个唯一ID (jti)
                .setSubject(username)
                .claim("role", role)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expireMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public static Claims parseToken(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody();
    }
}