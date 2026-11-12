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

    // 补齐 WebMvcConfig 与 AccessLogInterceptor 所需的依赖
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private MustChangePasswordCache mustChangePasswordCache;

    @MockitoBean
    private MeterRegistry meterRegistry; // 新增：Mock 掉监控组件

    @Test
    @DisplayName("学生登录：参数合法时返回 200 与 Token")
    void studentLoginSuccess() throws Exception {
        // 构建合法请求的 JSON
        String jsonContent = """
                {
                  "username": "S001",
                  "password": "password",
                  "captchaKey": "key",
                  "captchaCode": "code"
                }
                """;

        Map<String, Object> mockResult = new HashMap<>();
        mockResult.put("token", "mock-token");
        mockResult.put("refreshToken", "mock-rt");
        mockResult.put("user", new LoginUserVO("S001", "张三", "student"));

        // 当调用 Facade 时，返回假数据
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
        // 故意不传 username，其他必填项传上，确保唯一报错的是 username
        String jsonContent = """
                {
                  "password": "password",
                  "captchaKey": "mock-key",
                  "captchaCode": "mock-code"
                }
                """;

        mockMvc.perform(post("/api/login/student")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonContent))
                .andExpect(status().isOk()) // 异常被全局处理器包装成了 200
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("账号不能为空"));
    }
}