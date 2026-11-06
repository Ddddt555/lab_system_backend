package com.lab.labsystem.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lab.labsystem.config.RedisPubSubConfig;
import com.lab.labsystem.entity.ExpSession;
import com.lab.labsystem.entity.StudentBooking;
import com.lab.labsystem.mapper.ExpSessionMapper;
import com.lab.labsystem.mapper.StudentBookingMapper;
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
    private static ExpSessionMapper expSessionMapper;
    private static StudentBookingMapper studentBookingMapper;

    @Autowired
    public void setStringRedisTemplate(StringRedisTemplate template) {
        AttendanceWebSocket.stringRedisTemplate = template;
    }

    @Autowired
    public void setExpSessionMapper(ExpSessionMapper mapper) {
        AttendanceWebSocket.expSessionMapper = mapper;
    }

    @Autowired
    public void setStudentBookingMapper(StudentBookingMapper mapper) {
        AttendanceWebSocket.studentBookingMapper = mapper;
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
        String ticketValue = stringRedisTemplate.opsForValue().get(redisKey);

        if (ticketValue == null) {
            closeSession(session, "票据无效或已过期");
            return;
        }
        stringRedisTemplate.delete(redisKey);

        // 票据内容格式为 "工号/学号:角色"，必须先拆开，不能整体当作身份使用
        String[] parts = ticketValue.split(":");
        String userNo = parts.length > 0 ? parts[0] : null;
        String role = parts.length > 1 ? parts[1] : null;

        if (userNo == null || userNo.isEmpty()) {
            closeSession(session, "票据内容异常");
            return;
        }

        // 【安全加固】房间级授权：票据只证明"你是谁"，不证明"你能看这个场次"。
        // 必须校验该用户与本场次存在关系，否则任何合法登录用户都可以订阅任意场次的考勤广播。
        if (!isAllowedToJoinSession(userNo, role, sessionNo)) {
            closeSession(session, "无权访问该场次的考勤");
            return;
        }

        sessionPools.computeIfAbsent(sessionNo, k -> new CopyOnWriteArraySet<>()).add(session);
        activeTimeMap.put(session, System.currentTimeMillis());

        System.out.println("🔌 [考勤WS] 用户 " + userNo + "(" + role + ") 成功进入房间: " + sessionNo);
    }

    /**
     * 判断用户是否有权订阅指定场次的考勤广播。
     * 管理员放行；教师仅限本人授课场次；学生仅限本人已预约的场次。
     */
    private boolean isAllowedToJoinSession(String userNo, String role, String sessionNo) {
        if ("admin".equals(role)) {
            return true;
        }
        if (expSessionMapper == null || studentBookingMapper == null) {
            return false;
        }

        ExpSession expSession = expSessionMapper.selectById(sessionNo);
        if (expSession == null) {
            return false;
        }

        if ("teacher".equals(role)) {
            return userNo.equals(expSession.getWorkNo());
        }

        if ("student".equals(role)) {
            Long booked = studentBookingMapper.selectCount(new QueryWrapper<StudentBooking>()
                    .eq("student_no", userNo)
                    .eq("session_no", sessionNo)
                    .eq("status", 1));
            return booked != null && booked > 0;
        }

        return false;
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