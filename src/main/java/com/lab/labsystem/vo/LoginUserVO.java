package com.lab.labsystem.vo;

/**
 * 登录返回给前端的用户信息（脱敏版）
 * 绝不包含 password 字段
 */
public class LoginUserVO {

    private String userNo; // 学号 / 工号 / admin
    private String name;
    private String role;   // student / teacher / admin

    public LoginUserVO() {}

    public LoginUserVO(String userNo, String name, String role) {
        this.userNo = userNo;
        this.name = name;
        this.role = role;
    }

    public String getUserNo() { return userNo; }
    public void setUserNo(String userNo) { this.userNo = userNo; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}