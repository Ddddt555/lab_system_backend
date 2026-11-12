package com.lab.labsystem.service.impl;

import com.lab.labsystem.dto.request.LoginDTO;
import com.lab.labsystem.dto.request.RefreshTokenDTO;
import com.lab.labsystem.service.AuthFacade;
import com.lab.labsystem.service.AuthService;
import com.lab.labsystem.service.TokenService;
import com.lab.labsystem.vo.LoginUserVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AuthFacadeImpl implements AuthFacade {

    private final AuthService authService;
    private final TokenService tokenService;

    public AuthFacadeImpl(AuthService authService, TokenService tokenService) {
        this.authService = authService;
        this.tokenService = tokenService;
    }

    @Override
    public Object generateCaptcha() {
        return authService.generateCaptcha();
    }

    @Override
    public Map<String, Object> loginStudent(LoginDTO dto, HttpServletRequest request) {
        LoginUserVO userVO = authService.verifyStudent(dto, request);
        Map<String, Object> tokens = tokenService.createTokenPair(userVO.getUserNo(), "student", dto.getDeviceId());
        return assembleResult(userVO, tokens);
    }

    @Override
    public Map<String, Object> loginTeacher(LoginDTO dto, HttpServletRequest request) {
        LoginUserVO userVO = authService.verifyTeacher(dto, request);
        Map<String, Object> tokens = tokenService.createTokenPair(userVO.getUserNo(), "teacher", dto.getDeviceId());
        return assembleResult(userVO, tokens);
    }

    @Override
    public Map<String, Object> loginAdmin(LoginDTO dto, HttpServletRequest request) {
        LoginUserVO userVO = authService.verifyAdmin(dto, request);
        Map<String, Object> tokens = tokenService.createTokenPair(userVO.getUserNo(), "admin", dto.getDeviceId());
        return assembleResult(userVO, tokens);
    }

    @Override
    public Map<String, Object> testLogin(String username, String role, HttpServletRequest request) {
        LoginUserVO userVO = authService.verifyTestLogin(username, role, request);
        Map<String, Object> tokens = tokenService.createTokenPair(userVO.getUserNo(), role, "test-device");
        return assembleResult(userVO, tokens);
    }

    @Override
    public String logout(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        tokenService.revokeTokens(token);
        return "退出成功";
    }

    @Override
    public String issueWsTicket(String userNo, String role) {
        return tokenService.issueWsTicket(userNo, role);
    }

    @Override
    public Map<String, Object> refreshToken(RefreshTokenDTO dto) {
        return tokenService.refreshToken(dto.getRefreshToken(), dto.getDeviceId());
    }

    private Map<String, Object> assembleResult(LoginUserVO userVO, Map<String, Object> tokens) {
        Map<String, Object> data = new HashMap<>();
        data.put("user", userVO);
        data.put("token", tokens.get("token"));
        data.put("refreshToken", tokens.get("refreshToken"));
        return data;
    }
}