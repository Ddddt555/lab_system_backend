package com.lab.labsystem.common;

import java.util.regex.Pattern;

public class PasswordValidator {

    // 🛑 密码规则：至少 8 位，包含大写、小写、数字、特殊字符
    private static final String PASSWORD_PATTERN = 
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&._-])[A-Za-z\\d@$!%*?&._-]{8,}$";

    private static final Pattern pattern = Pattern.compile(PASSWORD_PATTERN);

    /**
     * 校验密码是否符合强度要求
     * @param password 明文密码
     * @return 如果不符合，返回错误提示；如果符合，返回 null
     */
    public static String validate(String password) {
        if (password == null || password.trim().isEmpty()) {
            return "新密码不能为空！";
        }
        if (password.length() < 8) {
            return "密码长度不能少于 8 位！";
        }
        if (!pattern.matcher(password).matches()) {
            return "密码必须包含大写字母、小写字母、数字和特殊字符（如 @$!%*?&._-）！";
        }
        // 禁止使用过于简单的弱口令
        if (password.equalsIgnoreCase("12345678") || password.equalsIgnoreCase("password")) {
            return "密码过于简单，请更换更复杂的密码！";
        }
        return null;
    }
}