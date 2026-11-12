package com.lab.labsystem.service.auth.impl;

import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.JwtUtil;
import com.lab.labsystem.entity.SysLog;
import com.lab.labsystem.mapper.SysLogMapper;
import com.lab.labsystem.service.auth.TokenService;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class TokenServiceImpl implements TokenService {

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String WS_TICKET_PREFIX = "ws:ticket:";
    private static final String USER_DEVICES_PREFIX = "user:devices:";
    private static final int MAX_DEVICES = 5;
    
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);
    private static final Duration WS_TICKET_TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate stringRedisTemplate;
    private final SysLogMapper logMapper;

    public TokenServiceImpl(StringRedisTemplate stringRedisTemplate, SysLogMapper logMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.logMapper = logMapper;
    }

    @Override
    public Map<String, Object> createTokenPair(String userNo, String role, String deviceId) {
        String accessToken = JwtUtil.generateAccessToken(userNo, role, deviceId);
        String refreshToken = JwtUtil.generateRefreshToken(userNo, role, deviceId);
        String redisKey = REFRESH_TOKEN_PREFIX + userNo + ":" + deviceId;
        
        String userDevicesKey = USER_DEVICES_PREFIX + userNo;
        long now = System.currentTimeMillis();
        stringRedisTemplate.opsForZSet().add(userDevicesKey, deviceId, now);
        stringRedisTemplate.expire(userDevicesKey, REFRESH_TOKEN_TTL);

        Long deviceCount = stringRedisTemplate.opsForZSet().zCard(userDevicesKey);
        if (deviceCount != null && deviceCount > MAX_DEVICES) {
            Set<String> oldestDevices = stringRedisTemplate.opsForZSet().range(userDevicesKey, 0, 0);
            if (oldestDevices != null && !oldestDevices.isEmpty()) {
                String oldestDeviceId = oldestDevices.iterator().next();
                stringRedisTemplate.opsForZSet().remove(userDevicesKey, oldestDeviceId);
                stringRedisTemplate.delete(REFRESH_TOKEN_PREFIX + userNo + ":" + oldestDeviceId);
                log.warn("用户 {} 的在线设备数超过 {} 台，已自动踢出最旧设备: {}", userNo, MAX_DEVICES, oldestDeviceId);
            }
        }

        stringRedisTemplate.opsForValue().set(redisKey, refreshToken, REFRESH_TOKEN_TTL);

        Map<String, Object> data = new HashMap<>();
        data.put("token", accessToken);
        data.put("refreshToken", refreshToken);
        return data;
    }

    @Override
    public Map<String, Object> refreshToken(String refreshToken, String deviceId) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new BusinessException("缺少刷新令牌！");
        }
        try {
            Claims claims = JwtUtil.parseToken(refreshToken);
            String userNo = claims.getSubject();
            String role = (String) claims.get("role");
            String type = (String) claims.get("type");
            String tokenDeviceId = (String) claims.get("deviceId");

            if (!"refresh".equals(type)) {
                throw new BusinessException("无效的刷新令牌类型！");
            }
            if (tokenDeviceId != null && deviceId != null && !tokenDeviceId.equals(deviceId)) {
                throw new BusinessException("设备环境异常，请重新登录！");
            }

            String redisKey = REFRESH_TOKEN_PREFIX + userNo + ":" + deviceId;
            String cachedToken = stringRedisTemplate.opsForValue().get(redisKey);
            
            if (cachedToken == null) {
                throw new BusinessException("刷新令牌已失效，请重新登录！");
            }

            if (!cachedToken.equals(refreshToken)) {
                stringRedisTemplate.delete(redisKey);
                SysLog securityLog = new SysLog();
                securityLog.setOperator("用户-" + userNo);
                securityLog.setAction("安全拦截：设备 " + deviceId + " 检测到无效刷新令牌，已强制该设备下线");
                securityLog.setCreateTime(LocalDateTime.now());
                securityLog.setSuccess(0);
                logMapper.insert(securityLog);

                throw new BusinessException("安全警告：检测到异常刷新操作，已被强制下线，请重新登录！");
            }

            stringRedisTemplate.delete(redisKey);
            String newAccessToken = JwtUtil.generateAccessToken(userNo, role, deviceId);
            String newRefreshToken = JwtUtil.generateRefreshToken(userNo, role, deviceId);

            stringRedisTemplate.opsForValue().set(redisKey, newRefreshToken, REFRESH_TOKEN_TTL);
            stringRedisTemplate.opsForZSet().add(USER_DEVICES_PREFIX + userNo, deviceId, System.currentTimeMillis());
            stringRedisTemplate.expire(USER_DEVICES_PREFIX + userNo, REFRESH_TOKEN_TTL);

            Map<String, Object> data = new HashMap<>();
            data.put("token", newAccessToken);
            data.put("refreshToken", newRefreshToken); 
            return data;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("刷新令牌无效或已过期！");
        }
    }

    @Override
    public void revokeTokens(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            try {
                Claims claims = JwtUtil.parseToken(token);
                String jti = claims.getId();
                String userNo = claims.getSubject();
                String deviceId = claims.get("deviceId", String.class);
                Date expiration = claims.getExpiration();
                long remainingSeconds = (expiration.getTime() - System.currentTimeMillis()) / 1000;

                if (remainingSeconds > 0 && jti != null) {
                    stringRedisTemplate.opsForValue().set(BLACKLIST_PREFIX + jti, "1", Duration.ofSeconds(remainingSeconds));
                }
                if (userNo != null && deviceId != null) {
                    stringRedisTemplate.delete(REFRESH_TOKEN_PREFIX + userNo + ":" + deviceId);
                    stringRedisTemplate.opsForZSet().remove(USER_DEVICES_PREFIX + userNo, deviceId);
                }
            } catch (Exception ignored) {}
        }
    }

    @Override
    public String issueWsTicket(String userNo, String role) {
        if (userNo == null) {
            throw new BusinessException("未登录，无法获取票据");
        }
        String ticket = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(WS_TICKET_PREFIX + ticket, userNo + ":" + role, WS_TICKET_TTL);
        return ticket;
    }

    @Override
    public boolean isBlacklisted(String jti) {
        return jti != null && Boolean.TRUE.equals(stringRedisTemplate.hasKey(BLACKLIST_PREFIX + jti));
    }
}