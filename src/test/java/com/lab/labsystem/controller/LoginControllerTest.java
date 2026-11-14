package com.lab.labsystem.controller;

import com.lab.labsystem.dto.request.LoginDTO;
import com.lab.labsystem.service.auth.AuthFacade;
import com.lab.labsystem.service.auth.MustChangePasswordCache;
import com.lab.labsystem.service.auth.TokenService;
import com.lab.labsystem.vo.LoginUserVO;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoginController.class)
class LoginControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthFacade authFacade;

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private MustChangePasswordCache mustChangePasswordCache;

    @MockitoBean
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("学生登录：参数合法时返回 200 与 Token")
    void studentLoginSuccess() throws Exception {
        String jsonContent = """
                {
                  "username": "S001",
                  "password": "password",
                  "captchaKey": "key",
                  "captchaCode": "code",
                  "deviceId": "test-device-1234567890"
                }
                """;

        Map<String, Object> mockResult = new HashMap<>();
        mockResult.put("token", "mock-token");
        mockResult.put("refreshToken", "mock-rt");
        mockResult.put("user", new LoginUserVO("S001", "张三", "student"));

        when(authFacade.loginStudent(any(LoginDTO.class), any())).thenReturn(mockResult);

        mockMvc.perform(post("/api/login/student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("mock-token"));
    }

    @Test
    @DisplayName("学生登录：参数为空时触发 @Valid 校验拦截，返回 400")
    void studentLoginValidationFail() throws Exception {
        String jsonContent = """
                {
                  "password": "password",
                  "captchaKey": "mock-key",
                  "captchaCode": "mock-code",
                  "deviceId": "test-device-1234567890"
                }
                """;

        mockMvc.perform(post("/api/login/student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("账号不能为空"));
    }
}