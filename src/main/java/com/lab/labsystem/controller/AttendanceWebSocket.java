package com.lab.labsystem.controller;

import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@ServerEndpoint("/ws/attendance/{sessionNo}")
public class AttendanceWebSocket {

    // ⚠️ 静态注入 Redis，因为 WebSocket 是多例的
    private static StringRedisTemplate stringRedisTemplate;

    @Autowired
    public void setStringRedisTemplate(StringRedisTemplate template) {
        AttendanceWebSocket.stringRedisTemplate = template;
    }

    // 存储房间信息：key: 场次号, value: 连接集合
    private static final ConcurrentHashMap<String, CopyOnWriteArraySet<Session>> sessionPools = new ConcurrentHashMap<>();
    
    // 存储心跳活跃时间：key: Session, value: 最后一次活跃时间戳
    private static final Map<Session, Long> activeTimeMap = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("sessionNo") String sessionNo) {
        // ⚠️ 从 URL 参数中获取 Ticket
        Map<String, java.util.List<String>> params = session.getRequestParameterMap();
        String ticket = (params.containsKey("ticket") && !params.get("ticket").isEmpty()) 
                ? params.get("ticket").get(0) : null;

        if (ticket == null) {
            closeSession(session, "缺少票据");
            return;
        }

        // 去 Redis 验证票据，并立刻删除（防重放攻击）
        String redisKey = "ws:ticket:" + ticket;
        String userNo = stringRedisTemplate.opsForValue().get(redisKey);
        
        if (userNo == null) {
            closeSession(session, "票据无效或已过期");
            return;
        }
        stringRedisTemplate.delete(redisKey); // 用完立刻销毁

        // 鉴权通过，加入房间
        sessionPools.computeIfAbsent(sessionNo, k -> new CopyOnWriteArraySet<>()).add(session);
        activeTimeMap.put(session, System.currentTimeMillis()); // 记录活跃时间
        
        System.out.println("🔌 [WebSocket] 用户 " + userNo + " 通过Ticket验证，成功进入房间: " + sessionNo);
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        // 收到任何消息，都刷新活跃时间
        activeTimeMap.put(session, System.currentTimeMillis());
        
        // 处理前端发来的心跳包
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

    // 广播消息
    public static void broadcast(String sessionNo, String message) {
        CopyOnWriteArraySet<Session> sessions = sessionPools.get(sessionNo);
        if (sessions != null) {
            for (Session session : sessions) {
                try {
                    session.getBasicRemote().sendText(message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // ⚠️ 定时任务：每 30 秒清理一次过期的死连接
    @Scheduled(fixedRate = 30000)
    public void checkHeartbeat() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Session, Long> entry : activeTimeMap.entrySet()) {
            // 如果超过 90 秒没有收到心跳，视为断线
            if (now - entry.getValue() > 90_000) {
                Session session = entry.getKey();
                System.out.println("💀 清理死连接: " + session.getId());
                try {
                    session.close();
                } catch (IOException ignored) {}
                activeTimeMap.remove(session);
            }
        }
    }
}