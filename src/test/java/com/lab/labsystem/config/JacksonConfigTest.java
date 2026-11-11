package com.lab.labsystem.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Jackson 配置的契约测试。
 *
 * <p>前端的日期显示完全依赖这里的全局格式（yyyy-MM-dd HH:mm:ss）。
 * 依赖升级（Jackson 2.17 → 2.18）属于跨小版本变更，一旦默认行为或模块
 * 注册方式变化，所有时间字段会静默变成 ISO 格式或时间戳 —— 编译期发现不了。
 *
 * <p>因此这里把"序列化成什么字符串"钉死，作为升级的回归网。
 */
/*class JacksonConfigTest {

    private ObjectMapper mapper() {
        return new JacksonConfig().jacksonBuilder().build();
    }

    @Test
    @DisplayName("LocalDateTime 序列化为 yyyy-MM-dd HH:mm:ss，而不是时间戳或 ISO")
    void serializeLocalDateTime() throws Exception {
        LocalDateTime time = LocalDateTime.of(2026, 11, 11, 9, 30, 15);

        String json = mapper().writeValueAsString(time);

        assertEquals("\"2026-11-11 09:30:15\"", json);
    }

    @Test
    @DisplayName("前端传来的同格式字符串能反序列化回 LocalDateTime")
    void deserializeLocalDateTime() throws Exception {
        LocalDateTime parsed = mapper().readValue("\"2026-11-11 09:30:15\"", LocalDateTime.class);

        assertEquals(LocalDateTime.of(2026, 11, 11, 9, 30, 15), parsed);
    }

    @Test
    @DisplayName("嵌套对象中的时间字段同样走该格式")
    void serializeNestedObject() throws Exception {
        Sample sample = new Sample();
        sample.setCreateTime(LocalDateTime.of(2026, 1, 2, 3, 4, 5));

        String json = mapper().writeValueAsString(sample);

        assertEquals("{\"createTime\":\"2026-01-02 03:04:05\"}", json);
    }

    static class Sample {
        private LocalDateTime createTime;

        public LocalDateTime getCreateTime() {
            return createTime;
        }

        public void setCreateTime(LocalDateTime createTime) {
            this.createTime = createTime;
        }
    }
}*/
