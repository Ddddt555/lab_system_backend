package com.lab.labsystem.service.impl;

import com.lab.labsystem.dto.request.LoginDTO;
import com.lab.labsystem.service.AuthService;
import com.lab.labsystem.service.TokenService;
import com.lab.labsystem.vo.LoginUserVO;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthFacadeImplTest {

    private AuthService authService;
    private TokenService tokenService;
    private AuthFacadeImpl facade;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        tokenService = mock(TokenService.class);
        facade = new AuthFacadeImpl(authService, tokenService);
    }

    @Test
    @DisplayName("学生登录：先认证，后发 Token，并组装返回")
    void loginStudent() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        LoginDTO dto = new LoginDTO();
        dto.setUsername("S001");
        dto.setDeviceId("test-device");

        // Mock 认证层
        LoginUserVO userVO = new LoginUserVO("S001", "张三", "student");
        userVO.setMustChangePassword(false);
        when(authService.verifyStudent(dto, request)).thenReturn(userVO);

        // Mock Token 层
        Map<String, Object> tokens = new HashMap<>();
        tokens.put("token", "mock-access-token");
        tokens.put("refreshToken", "mock-refresh-token");
        when(tokenService.createTokenPair("S001", "student", "test-device")).thenReturn(tokens);

        // 执行
        Map<String, Object> result = facade.loginStudent(dto, request);

        // 验证
        assertNotNull(result);
        assertEquals("mock-access-token", result.get("token"));
        assertEquals("mock-refresh-token", result.get("refreshToken"));
        assertEquals(userVO, result.get("user"));
        
        // 验证是否确实调用了两次
        verify(authService).verifyStudent(dto, request);
        verify(tokenService).createTokenPair("S001", "student", "test-device");
    }
}