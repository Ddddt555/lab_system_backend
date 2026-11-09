package com.lab.labsystem.common;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class PasswordValidator {

    // 密码规则：至少 8 位，包含大写、小写、数字、特殊字符
    private static final String PASSWORD_PATTERN =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&._-])[A-Za-z\\d@$!%*?&._-]{8,}$";

    private static final Pattern PATTERN = Pattern.compile(PASSWORD_PATTERN);

    /**
     * 常见弱口令黑名单（统一按小写比较）。
     *
     * <p>其中一部分本身就不满足复杂度要求（如 12345678、password），
     * 另一些则<b>能通过</b>复杂度校验（如 Aa123456!、Qwer1234!），
     * 正是后者让这个黑名单真正有意义。
     */
    private static final Set<String> WEAK_PASSWORDS = Set.of(
            "12345678", "123456789", "87654321",
            "password", "passw0rd", "p@ssw0rd",
            "aa123456", "aa123456!", "abc12345", "abcd1234", "abcd1234!",
            "qwer1234", "qwer1234!", "1qaz2wsx", "1qaz2wsx!",
            "admin123", "admin123!", "iloveyou"
    );

    /**
     * 校验密码是否符合强度要求。
     *
     * <p>校验顺序是有意为之：空 → 长度 → 弱口令 → 复杂度。
     * 弱口令判断必须放在复杂度之前，否则像 12345678、password 这类
     * 口令会先被复杂度规则拦下，永远走不到弱口令分支，让黑名单形同虚设。
     *
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
        if (WEAK_PASSWORDS.contains(password.toLowerCase(Locale.ROOT))) {
            return "密码过于简单，请更换更复杂的密码！";
        }
        if (!PATTERN.matcher(password).matches()) {
            return "密码必须包含大写字母、小写字母、数字和特殊字符（如 @$!%*?&._-）！";
        }
        return null;
    }
}
