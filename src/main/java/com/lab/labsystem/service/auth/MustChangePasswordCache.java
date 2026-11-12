package com.lab.labsystem.service.auth;

import com.lab.labsystem.entity.Student;
import com.lab.labsystem.entity.SysAdmin;
import com.lab.labsystem.entity.Teacher;
import com.lab.labsystem.mapper.StudentMapper;
import com.lab.labsystem.mapper.SysAdminMapper;
import com.lab.labsystem.mapper.TeacherMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * “是否必须先修改初始密码”的缓存。
 *
 * <p>原先 {@code JwtInterceptor} 对<b>每一个请求</b>都要按角色 selectById 查一次
 * 学生/教师/管理员表，只为读取 must_change_password 这一个字段。
 * 在并发下这是纯浪费的数据库压力，而且这条查询发生在所有业务逻辑之前。
 *
 * <p>这里用 Redis 缓存该标记：
 * <ul>
 *   <li>TTL 60 秒，即使某处漏了失效调用，最多也只脏 60 秒；</li>
 *   <li>所有会修改该标记的地方（改密、管理员重置密码）都显式 evict；</li>
 *   <li>Redis 异常时降级为直接查库，不因为缓存故障把用户挡在门外。</li>
 * </ul>
 */
@Slf4j
@Component
public class MustChangePasswordCache {

    private static final String KEY_PREFIX = "pwd:must_change:";
    private static final Duration TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;
    private final StudentMapper studentMapper;
    private final TeacherMapper teacherMapper;
    private final SysAdminMapper sysAdminMapper;

    public MustChangePasswordCache(StringRedisTemplate redis,
                                   StudentMapper studentMapper,
                                   TeacherMapper teacherMapper,
                                   SysAdminMapper sysAdminMapper) {
        this.redis = redis;
        this.studentMapper = studentMapper;
        this.teacherMapper = teacherMapper;
        this.sysAdminMapper = sysAdminMapper;
    }

    /**
     * 判断该用户是否处于“必须修改初始密码”状态。
     *
     * @param role   角色：student / teacher / admin
     * @param userNo 学号 / 工号 / 管理员账号
     */
    public boolean isRequired(String role, String userNo) {
        if (role == null || userNo == null || userNo.isEmpty()) {
            return false;
        }
        String key = cacheKey(role, userNo);

        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                return "1".equals(cached);
            }
        } catch (Exception e) {
            // 缓存不可用时降级查库，不能因为 Redis 抖动把正常用户拒之门外
            log.warn("读取强制改密缓存失败，降级为查库: {}", e.getMessage());
            return queryFromDb(role, userNo);
        }

        boolean required = queryFromDb(role, userNo);
        try {
            redis.opsForValue().set(key, required ? "1" : "0", TTL);
        } catch (Exception e) {
            log.warn("写入强制改密缓存失败: {}", e.getMessage());
        }
        return required;
    }

    /**
     * 清除缓存。改密成功、管理员重置密码后必须调用。
     */
    public void evict(String role, String userNo) {
        if (role == null || userNo == null || userNo.isEmpty()) {
            return;
        }
        try {
            redis.delete(cacheKey(role, userNo));
        } catch (Exception e) {
            // 失效失败不影响主流程，TTL 会兜底
            log.warn("清除强制改密缓存失败，将在 {} 秒后自动过期: {}", TTL.getSeconds(), e.getMessage());
        }
    }

    private String cacheKey(String role, String userNo) {
        return KEY_PREFIX + role + ":" + userNo;
    }

    private boolean queryFromDb(String role, String userNo) {
        switch (role) {
            case "student": {
                Student s = studentMapper.selectById(userNo);
                return s != null && Integer.valueOf(1).equals(s.getMustChangePassword());
            }
            case "teacher": {
                Teacher t = teacherMapper.selectById(userNo);
                return t != null && Integer.valueOf(1).equals(t.getMustChangePassword());
            }
            case "admin": {
                SysAdmin a = sysAdminMapper.selectById(userNo);
                return a != null && Integer.valueOf(1).equals(a.getMustChangePassword());
            }
            default:
                return false;
        }
    }
}
