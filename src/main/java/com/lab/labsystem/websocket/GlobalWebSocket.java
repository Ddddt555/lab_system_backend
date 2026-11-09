package com.lab.labsystem.websocket;

import com.lab.labsystem.config.RedisPubSubConfig;
import jakarta.websocket.*;
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
import lombok.extern.slf4j.Slf4j;

@Component
@ServerEndpoint("/ws/global")
@Slf4j
public class GlobalWebSocket implements MessageListener {

    private static StringRedisTemplate stringRedisTemplate;

    @Autowired
    public void setStringRedisTemplate(StringRedisTemplate template) {
        GlobalWebSocket.stringRedisTemplate = template;
    }

    private static final CopyOnWriteArraySet<Session> allSessions = new CopyOnWriteArraySet<>();
    private static final ConcurrentHashMap<String, CopyOnWriteArraySet<Session>> roleRooms = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, CopyOnWriteArraySet<Session>> userRooms = new ConcurrentHashMap<>();
    private static final Map<Session, Long> activeTimeMap = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session) {
        Map<String, java.util.List<String>> params = session.getRequestParameterMap();
        String ticket = (params.containsKey("ticket") && !params.get("ticket").isEmpty())
                ? params.get("ticket").get(0) : null;

        if (ticket == null) { closeSession(session, "缺少Ticket"); return; }

        String redisKey = "ws:ticket:" + ticket;
        String userInfo = stringRedisTemplate.opsForValue().get(redisKey);

        if (userInfo == null) { closeSession(session, "票据无效"); return; }
        stringRedisTemplate.delete(redisKey);

        String[] parts = userInfo.split(":");
        String userNo = parts.length > 0 ? parts[0] : null;
        String role = parts.length > 1 ? parts[1] : "all";

        if (userNo == null || userNo.isEmpty()) { closeSession(session, "票据内容异常"); return; }

        allSessions.add(session);
        roleRooms.computeIfAbsent(role, k -> new CopyOnWriteArraySet<>()).add(session);
        userRooms.computeIfAbsent(userNo, k -> new CopyOnWriteArraySet<>()).add(session);
        activeTimeMap.put(session, System.currentTimeMillis());

        log.info("📢 [全局WS] 用户上线: {} (角色: {})", userNo, role);
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
        userRooms.entrySet().removeIf(entry -> {
            entry.getValue().remove(session);
            return entry.getValue().isEmpty();
        });
        activeTimeMap.remove(session);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("❌ [全局WS] WebSocket 连接异常", error);
    }

    private void closeSession(Session session, String reason) {
        try { session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, reason)); } catch (IOException ignored) {}
    }

    // =========================================================
    // 🛑【核心改造】发往 Redis 频道
    // =========================================================
    public static void broadcastToRole(String role, String message) {
        if (stringRedisTemplate != null) {
            // 格式: ROLE:role:message
            stringRedisTemplate.convertAndSend(RedisPubSubConfig.GLOBAL_CHANNEL, "ROLE:" + role + ":" + message);
        }
    }

    public static int sendToUser(String userNo, String message) {
        if (stringRedisTemplate != null) {
            // 格式: USER:userNo:message
            stringRedisTemplate.convertAndSend(RedisPubSubConfig.GLOBAL_CHANNEL, "USER:" + userNo + ":" + message);
            return 1; // 统一返回 1（已投递到 Redis），让上层逻辑不报错
        }
        return 0;
    }

    // =========================================================
    // 🛑【核心改造】监听 Redis 消息，推给本地用户
    // =========================================================
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String msgStr = new String(message.getBody());
            if (msgStr.startsWith("ROLE:")) {
                String[] parts = msgStr.split(":", 3);
                if (parts.length < 3) return;
                String role = parts[1];
                String actualMsg = parts[2];
                doBroadcastToRoleLocal(role, actualMsg);
            } else if (msgStr.startsWith("USER:")) {
                String[] parts = msgStr.split(":", 3);
                if (parts.length < 3) return;
                String userNo = parts[1];
                String actualMsg = parts[2];
                doSendToUserLocal(userNo, actualMsg);
            }
        } catch (Exception e) {
            log.error("❌ [全局WS] 处理 Redis 消息失败", e);
        }
    }

    private static void doBroadcastToRoleLocal(String role, String message) {
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

    private static void doSendToUserLocal(String userNo, String message) {
        CopyOnWriteArraySet<Session> sessions = userRooms.get(userNo);
        if (sessions != null && !sessions.isEmpty()) {
            for (Session session : sessions) {
                if (!session.isOpen()) continue;
                try { session.getBasicRemote().sendText(message); } catch (IOException ignored) {}
            }
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