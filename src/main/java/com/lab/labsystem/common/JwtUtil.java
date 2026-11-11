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
import java.util.UUID;

@Component
public class JwtUtil {

    private static String secret;
    private static long accessExpireMillis;
    private static long refreshExpireMillis;
    private static Key key;

    @Value("${jwt.secret}")
    public void setSecret(String secret) { JwtUtil.secret = secret; }

    // 访问令牌（Access Token）过期时间，默认 30 分钟
    @Value("${jwt.access-expire-minutes:30}")
    public void setAccessExpireMinutes(long minutes) {
        JwtUtil.accessExpireMillis = minutes * 60 * 1000;
    }

    // 刷新令牌（Refresh Token）过期时间，默认 7 天
    @Value("${jwt.refresh-expire-days:7}")
    public void setRefreshExpireDays(long days) {
        JwtUtil.refreshExpireMillis = days * 24 * 60 * 60 * 1000;
    }

    @PostConstruct
    public void init() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("jwt.secret 长度必须 >= 32 字节");
        }
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    // 签发短效 Access Token
    public static String generateAccessToken(String username, String role, String deviceId) {
        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setSubject(username)
                .claim("role", role)
                .claim("type", "access") // 标识类型
                .claim("deviceId", deviceId) // 绑定设备指纹
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + accessExpireMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // 签发长效 Refresh Token
    public static String generateRefreshToken(String username, String role, String deviceId) {
        return Jwts.builder()
                .setId(UUID.randomUUID().toString())
                .setSubject(username)
                .claim("role", role)
                .claim("type", "refresh") // 标识类型
                .claim("deviceId", deviceId) // 绑定设备指纹
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + refreshExpireMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public static Claims parseToken(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody();
    }
}