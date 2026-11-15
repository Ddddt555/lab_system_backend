package com.lab.labsystem.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JacksonConfigTest {

    private ObjectMapper mapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JacksonConfig().localDateTimeModule());
        return objectMapper;
    }

    @Test
    @DisplayName("LocalDateTime 序列化为 yyyy-MM-dd HH:mm:ss")
    void serializeLocalDateTime() throws Exception {
        LocalDateTime time = LocalDateTime.of(2026, 11, 11, 9, 30, 15);
        String json = mapper().writeValueAsString(time);
        assertEquals("\"2026-11-11 09:30:15\"", json);
    }

    @Test
    @DisplayName("反序列化同格式字符串")
    void deserializeLocalDateTime() throws Exception {
        LocalDateTime parsed = mapper().readValue("\"2026-11-11 09:30:15\"", LocalDateTime.class);
        assertEquals(LocalDateTime.of(2026, 11, 11, 9, 30, 15), parsed);
    }
}