package com.lab.labsystem.service;

import com.lab.labsystem.dto.request.LoginDTO;
import com.lab.labsystem.dto.request.RefreshTokenDTO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

public interface AuthService {

    Object generateCaptcha();

    Map<String, Object> loginStudent(LoginDTO dto, HttpServletRequest request);

    Map<String, Object> loginTeacher(LoginDTO dto, HttpServletRequest request);

    Map<String, Object> loginAdmin(LoginDTO dto, HttpServletRequest request);

    Map<String, Object> testLogin(String username, String role, HttpServletRequest request);

    String logout(HttpServletRequest request);

    String issueWsTicket(String userNo, String role);

    Map<String, Object> refreshToken(RefreshTokenDTO dto);
}