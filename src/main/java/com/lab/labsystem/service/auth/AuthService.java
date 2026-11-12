package com.lab.labsystem.service.auth;

import com.lab.labsystem.dto.request.LoginDTO;
import com.lab.labsystem.vo.LoginUserVO;
import jakarta.servlet.http.HttpServletRequest;

public interface AuthService {
    Object generateCaptcha();
    
    // 只返回用户信息，不处理 Token
    LoginUserVO verifyStudent(LoginDTO dto, HttpServletRequest request);
    LoginUserVO verifyTeacher(LoginDTO dto, HttpServletRequest request);
    LoginUserVO verifyAdmin(LoginDTO dto, HttpServletRequest request);
    LoginUserVO verifyTestLogin(String username, String role, HttpServletRequest request);
}