package com.lab.labsystem.controller;

import com.lab.labsystem.config.RedisPubSubConfig;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@ServerEndpoint("/ws/attendance/{sessionNo}")
public class AttendanceWebSocket implements MessageListener {

    private static StringRedisTemplate stringRedisTemplate;

    @Autowired
    public void setStringRedisTemplate(StringRedisTemplate template) {
        AttendanceWebSocket.stringRedisTemplate = template;
    }

    // 存储房间信息：key: 场次号, value: 连接集合
    private static final ConcurrentHashMap<String, CopyOnWriteArraySet<Session>> sessionPools = new ConcurrentHashMap<>();
    
    // 存储心跳活跃时间
    private static final Map<Session, Long> activeTimeMap = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("sessionNo") String sessionNo) {
        Map<String, java.util.List<String>> params = session.getRequestParameterMap();
        String ticket = (params.containsKey("ticket") && !params.get("ticket").isEmpty()) 
                ? params.get("ticket").get(0) : null;

        if (ticket == null) {
            closeSession(session, "缺少票据");
            return;
        }

        String redisKey = "ws:ticket:" + ticket;
        String userNo = stringRedisTemplate.opsForValue().get(redisKey);
        
        if (userNo == null) {
            closeSession(session, "票据无效或已过期");
            return;
        }
        stringRedisTemplate.delete(redisKey);

        sessionPools.computeIfAbsent(sessionNo, k -> new CopyOnWriteArraySet<>()).add(session);
        activeTimeMap.put(session, System.currentTimeMillis());
        
        System.out.println("🔌 [考勤WS] 用户 " + userNo + " 成功进入房间: " + sessionNo);
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        activeTimeMap.put(session, System.currentTimeMillis());
        if ("PING".equalsIgnoreCase(message)) {
            try {
                session.getBasicRemote().sendText("PONG");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @OnClose
    public void onClose(Session session, @PathParam("sessionNo") String sessionNo) {
        CopyOnWriteArraySet<Session> sessions = sessionPools.get(sessionNo);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) sessionPools.remove(sessionNo);
        }
        activeTimeMap.remove(session);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        error.printStackTrace();
    }

    private void closeSession(Session session, String reason) {
        try {
            session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, reason));
        } catch (IOException ignored) {}
    }

    // =========================================================
    // 🛑【核心改造】广播消息：不再直接发本地，而是发到 Redis 频道
    // =========================================================
    public static void broadcast(String sessionNo, String message) {
        if (stringRedisTemplate != null) {
            // 格式: sessionNo:message
            stringRedisTemplate.convertAndSend(RedisPubSubConfig.ATTENDANCE_CHANNEL, sessionNo + ":" + message);
        }
    }

    // =========================================================
    // 🛑【核心改造】监听 Redis 消息，只推给连在当前服务器的用户
    // =========================================================
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String msgStr = new String(message.getBody());
            String[] parts = msgStr.split(":", 2);
            if (parts.length < 2) return;
            
            String sessionNo = parts[0];
            String actualMsg = parts[1];

            CopyOnWriteArraySet<Session> sessions = sessionPools.get(sessionNo);
            if (sessions != null) {
                for (Session session : sessions) {
                    try {
                        session.getBasicRemote().sendText(actualMsg);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ [考勤WS] 处理 Redis 消息失败: " + e.getMessage());
        }
    }

    @Scheduled(fixedRate = 30000)
    public void checkHeartbeat() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Session, Long> entry : activeTimeMap.entrySet()) {
            if (now - entry.getValue() > 90_000) {
                Session session = entry.getKey();
                try { session.close(); } catch (IOException ignored) {}
                activeTimeMap.remove(session);
            }
        }
    }
}