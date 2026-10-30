package com.lab.labsystem.controller;

import jakarta.websocket.*;
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
@ServerEndpoint("/ws/global")
public class GlobalWebSocket {

    private static StringRedisTemplate stringRedisTemplate;

    @Autowired
    public void setStringRedisTemplate(StringRedisTemplate template) {
        GlobalWebSocket.stringRedisTemplate = template;
    }

    // 全体在线连接
    private static final CopyOnWriteArraySet<Session> allSessions = new CopyOnWriteArraySet<>();
    // 角色房间
    private static final ConcurrentHashMap<String, CopyOnWriteArraySet<Session>> roleRooms = new ConcurrentHashMap<>();
    // ⚠️ 新增：账号 -> 连接集合（同一账号可多端登录，如 PC + 手机）
    private static final ConcurrentHashMap<String, CopyOnWriteArraySet<Session>> userRooms = new ConcurrentHashMap<>();
    // 心跳活跃时间
    private static final Map<Session, Long> activeTimeMap = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session) {
        Map<String, java.util.List<String>> params = session.getRequestParameterMap();
        String ticket = (params.containsKey("ticket") && !params.get("ticket").isEmpty())
                ? params.get("ticket").get(0) : null;

        if (ticket == null) { closeSession(session, "缺少Ticket"); return; }

        String redisKey = "ws:ticket:" + ticket;
        // LoginController 存的是 "userNo:role"
        String userInfo = stringRedisTemplate.opsForValue().get(redisKey);

        if (userInfo == null) { closeSession(session, "票据无效"); return; }
        stringRedisTemplate.delete(redisKey);

        String[] parts = userInfo.split(":");
        String userNo = parts.length > 0 ? parts[0] : null;
        String role = parts.length > 1 ? parts[1] : "all";

        if (userNo == null || userNo.isEmpty()) { closeSession(session, "票据内容异常"); return; }

        allSessions.add(session);
        roleRooms.computeIfAbsent(role, k -> new CopyOnWriteArraySet<>()).add(session);
        // ⚠️ 新增：登记账号到连接
        userRooms.computeIfAbsent(userNo, k -> new CopyOnWriteArraySet<>()).add(session);
        activeTimeMap.put(session, System.currentTimeMillis());

        System.out.println("📢 [全局WS] 用户上线: " + userNo + " (角色: " + role + ")，当前总连接: " + allSessions.size());
    }

    @OnMessage
    public void onMessage(Session session, String message) {
        activeTimeMap.put(session, System.currentTimeMillis());
        if ("PING".equalsIgnoreCase(message)) {
            try { session.getBasicRemote().sendText("PONG"); } catch (IOException ignored) {}
        }
    }

    @OnClose
    public void onClose(Session session) {
        allSessions.remove(session);
        roleRooms.values().forEach(set -> set.remove(session));
        // ⚠️ 新增：从 userRooms 中移除，并在 Set 空时移除 key
        userRooms.entrySet().removeIf(entry -> {
            entry.getValue().remove(session);
            return entry.getValue().isEmpty();
        });
        activeTimeMap.remove(session);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        error.printStackTrace();
    }

    private void closeSession(Session session, String reason) {
        try { session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, reason)); } catch (IOException ignored) {}
    }

    public static void broadcastToRole(String role, String message) {
        if ("all".equals(role)) {
            for (Session session : allSessions) {
                try { session.getBasicRemote().sendText(message); } catch (IOException ignored) {}
            }
            return;
        }
        CopyOnWriteArraySet<Session> sessions = roleRooms.get(role);
        if (sessions != null) {
            for (Session session : sessions) {
                try { session.getBasicRemote().sendText(message); } catch (IOException ignored) {}
            }
        }
    }

    /**
     * ⚠️ 新增：点对点发送给指定账号（支持多端同时收到）
     * @return 实际发送成功的连接数；0 表示该账号当前不在线
     */
    public static int sendToUser(String userNo, String message) {
        if (userNo == null || message == null) return 0;
        CopyOnWriteArraySet<Session> sessions = userRooms.get(userNo);
        if (sessions == null || sessions.isEmpty()) return 0;

        int sent = 0;
        for (Session session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                session.getBasicRemote().sendText(message);
                sent++;
            } catch (IOException e) {
                sessions.remove(session);
            }
        }
        return sent;
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