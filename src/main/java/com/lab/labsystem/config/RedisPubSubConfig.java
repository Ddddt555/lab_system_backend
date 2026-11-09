package com.lab.labsystem.config;

import com.lab.labsystem.websocket.AttendanceWebSocket;
import com.lab.labsystem.websocket.GlobalWebSocket;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisPubSubConfig {

    public static final String GLOBAL_CHANNEL = "ws:channel:global";
    public static final String ATTENDANCE_CHANNEL = "ws:channel:attendance";

    /**
     * 🛑【核心新增】配置 Redis 消息监听容器
     * 让 Spring Boot 自动去监听这两个频道
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory connectionFactory,
                                                                       GlobalWebSocket globalWebSocket,
                                                                       AttendanceWebSocket attendanceWebSocket) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        
        // 注册监听器：把频道和对应的 WebSocket 处理器绑定
        container.addMessageListener(globalWebSocket, new PatternTopic(GLOBAL_CHANNEL));
        container.addMessageListener(attendanceWebSocket, new PatternTopic(ATTENDANCE_CHANNEL));
        
        return container;
    }
}