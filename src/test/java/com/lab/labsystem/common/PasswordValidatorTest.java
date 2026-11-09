package com.lab.labsystem.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 密码强度校验的单元测试。
 *
 * <p>重点锁住校验顺序：弱口令判断必须在复杂度校验之前，
 * 否则 12345678 / password 这类口令会先被复杂度规则拦下，
 * 弱口令黑名单永远不会命中（这正是本次修复的缺陷）。
 */
class PasswordValidatorTest {

    @Test
    @DisplayName("空密码被拒绝")
    void rejectEmpty() {
        assertEquals("新密码不能为空！", PasswordValidator.validate(null));
        assertEquals("新密码不能为空！", PasswordValidator.validate(""));
        assertEquals("新密码不能为空！", PasswordValidator.validate("   "));
    }

    @Test
    @DisplayName("长度不足优先于其他规则")
    void rejectTooShort() {
        assertEquals("密码长度不能少于 8 位！", PasswordValidator.validate("abc"));
        // 弱口令且长度不足时，长度提示更具体
        assertEquals("密码长度不能少于 8 位！", PasswordValidator.validate("1234567"));
    }

    @Test
    @DisplayName("弱口令在复杂度校验之前被识别")
    void weakPasswordBeatsComplexityCheck() {
        // 这两者本来就不满足复杂度，修复前会返回"必须包含..."，弱口令分支不可达
        assertEquals("密码过于简单，请更换更复杂的密码！", PasswordValidator.validate("12345678"));
        assertEquals("密码过于简单，请更换更复杂的密码！", PasswordValidator.validate("password"));
        // 大小写不敏感
        assertEquals("密码过于简单，请更换更复杂的密码！", PasswordValidator.validate("PassWord"));
    }

    @Test
    @DisplayName("能通过复杂度校验的常见弱口令同样被拒绝")
    void rejectWeakButComplexPassword() {
        // 这些口令满足"大小写+数字+特殊字符"，只能靠黑名单拦住
        assertEquals("密码过于简单，请更换更复杂的密码！", PasswordValidator.validate("Aa123456!"));
        assertEquals("密码过于简单，请更换更复杂的密码！", PasswordValidator.validate("Qwer1234!"));
        assertEquals("密码过于简单，请更换更复杂的密码！", PasswordValidator.validate("P@ssw0rd"));
    }

    @Test
    @DisplayName("不含特殊字符时提示复杂度要求")
    void rejectMissingComplexity() {
        assertEquals("密码必须包含大写字母、小写字母、数字和特殊字符（如 @$!%*?&._-）！",
                PasswordValidator.validate("abcdefgh"));
        assertEquals("密码必须包含大写字母、小写字母、数字和特殊字符（如 @$!%*?&._-）！",
                PasswordValidator.validate("Abcdefg1"));
    }

    @Test
    @DisplayName("符合全部要求的密码通过")
    void acceptStrongPassword() {
        assertNull(PasswordValidator.validate("Abc123@x"));
        assertNull(PasswordValidator.validate("Lab@2025sys"));
    }
}
