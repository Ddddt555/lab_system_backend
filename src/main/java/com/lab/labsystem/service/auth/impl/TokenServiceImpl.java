package com.lab.labsystem.service.auth.impl;

import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.ErrorCode;
import com.lab.labsystem.common.JwtUtil;
import com.lab.labsystem.entity.SysLog;
import com.lab.labsystem.mapper.SysLogMapper;
import com.lab.labsystem.service.auth.TokenService;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class TokenServiceImpl implements TokenService {

    private static final Logger securityLog = LoggerFactory.getLogger("SECURITY_AUDIT");

    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String WS_TICKET_PREFIX = "ws:ticket:";
    private static final String USER_DEVICES_PREFIX = "user:devices:";
    private static final int MAX_DEVICES = 5;
    
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);
    private static final Duration WS_TICKET_TTL = Duration.ofSeconds(30);

    /**
     * Lua 脚本：保证“比对旧 Token 并替换为新 Token”的原子性。
     * 避免并发下多个请求同时通过校验，或者相互覆盖。
     */
    private static final String REFRESH_TOKEN_LUA_SCRIPT =
            "if redis.call('GET', KEYS[1]) == ARGV[1] then " +
            "   redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3]) " +
            "   return 1 " +
            "else " +
            "   return 0 " +
            "end";

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
                securityLog.warn("【设备超限】用户 {} 的在线设备数超过 {} 台，已自动踢出最旧设备: {}", userNo, MAX_DEVICES, oldestDeviceId);
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
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "缺少刷新令牌！");
        }
        
        // 强制要求刷新时必须携带设备指纹
        if (deviceId == null || deviceId.trim().isEmpty()) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "缺少设备标识，请求非法！");
        }

        try {
            Claims claims = JwtUtil.parseToken(refreshToken);
            String userNo = claims.getSubject();
            String role = (String) claims.get("role");
            String type = (String) claims.get("type");
            String tokenDeviceId = (String) claims.get("deviceId");

            if (!"refresh".equals(type)) {
                throw BusinessException.of(ErrorCode.TOKEN_INVALID, "无效的刷新令牌类型！");
            }
            
            // 严格校验设备指纹，如果 Token 里绑定了设备，必须与请求的设备一致
            if (tokenDeviceId != null && !tokenDeviceId.equals(deviceId)) {
                securityLog.warn("【设备异常】刷新令牌中的设备指纹不匹配，用户: {}, 请求设备: {}, Token设备: {}", userNo, deviceId, tokenDeviceId);
                throw BusinessException.of(ErrorCode.TOKEN_INVALID, "设备环境异常，请重新登录！");
            }

            String redisKey = REFRESH_TOKEN_PREFIX + userNo + ":" + deviceId;
            
            // 生成新 Token
            String newAccessToken = JwtUtil.generateAccessToken(userNo, role, deviceId);
            String newRefreshToken = JwtUtil.generateRefreshToken(userNo, role, deviceId);

            // 使用 Lua 脚本进行原子比对与替换
            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(REFRESH_TOKEN_LUA_SCRIPT, Long.class);
            Long result = stringRedisTemplate.execute(
                    redisScript,
                    Collections.singletonList(redisKey),
                    refreshToken,
                    newRefreshToken,
                    String.valueOf(REFRESH_TOKEN_TTL.getSeconds())
            );

            // 如果返回 0，说明旧 Token 不匹配（可能已失效，也可能是并发下被别的线程刷掉了）
            if (result == null || result != 1L) {
                // 安全防御：直接清除该设备 Token
                stringRedisTemplate.delete(redisKey);
                
                // 写入数据库审计
                SysLog dbLog = new SysLog();
                dbLog.setOperator("用户-" + userNo);
                dbLog.setAction("安全拦截：设备 " + deviceId + " 检测到无效刷新令牌，已强制该设备下线");
                dbLog.setCreateTime(LocalDateTime.now());
                dbLog.setSuccess(0);
                logMapper.insert(dbLog);

                // 写入独立安全日志
                securityLog.warn("【重放攻击拦截】用户: {}, 设备: {}, 使用了已失效或被替换的 RefreshToken，已强制下线", userNo, deviceId);

                throw BusinessException.of(ErrorCode.FORBIDDEN, "安全警告：检测到异常刷新操作，已被强制下线，请重新登录！");
            }

            // 原子替换成功，更新设备活跃时间
            stringRedisTemplate.opsForZSet().add(USER_DEVICES_PREFIX + userNo, deviceId, System.currentTimeMillis());
            stringRedisTemplate.expire(USER_DEVICES_PREFIX + userNo, REFRESH_TOKEN_TTL);

            Map<String, Object> data = new HashMap<>();
            data.put("token", newAccessToken);
            data.put("refreshToken", newRefreshToken); 
            return data;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw BusinessException.of(ErrorCode.TOKEN_INVALID, "刷新令牌无效或已过期！");
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
            throw BusinessException.of(ErrorCode.UNAUTHORIZED, "未登录，无法获取票据");
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