package com.lab.labsystem.service;

import com.lab.labsystem.entity.Student;
import com.lab.labsystem.mapper.StudentMapper;
import com.lab.labsystem.mapper.SysAdminMapper;
import com.lab.labsystem.mapper.TeacherMapper;
import com.lab.labsystem.service.auth.MustChangePasswordCache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 强制改密缓存的单元测试。
 *
 * <p>该缓存位于鉴权链路上，一旦判断错误会直接影响用户能否访问接口，
 * 因此单独覆盖：命中、回填、Redis 故障降级、失效四个关键路径。
 */
class MustChangePasswordCacheTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private StudentMapper studentMapper;
    private MustChangePasswordCache cache;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        studentMapper = mock(StudentMapper.class);
        TeacherMapper teacherMapper = mock(TeacherMapper.class);
        SysAdminMapper sysAdminMapper = mock(SysAdminMapper.class);

        cache = new MustChangePasswordCache(redis, studentMapper, teacherMapper, sysAdminMapper);
    }

    @Test
    @DisplayName("命中缓存时不再查询数据库")
    void cacheHitSkipsDatabase() {
        when(ops.get("pwd:must_change:student:2021001")).thenReturn("1");

        assertTrue(cache.isRequired("student", "2021001"));
        verifyNoInteractions(studentMapper);
    }

    @Test
    @DisplayName("未命中时查库并回填缓存（带 TTL）")
    void cacheMissQueriesDatabaseAndFillsCache() {
        when(ops.get(anyString())).thenReturn(null);

        Student student = new Student();
        student.setMustChangePassword(1);
        when(studentMapper.selectById("2021001")).thenReturn(student);

        assertTrue(cache.isRequired("student", "2021001"));

        verify(studentMapper).selectById("2021001");
        verify(ops).set(eq("pwd:must_change:student:2021001"), eq("1"), any(Duration.class));
    }

    @Test
    @DisplayName("Redis 故障时降级查库，不把用户误挡在门外")
    void redisFailureFallsBackToDatabase() {
        when(ops.get(anyString())).thenThrow(new RuntimeException("redis down"));

        Student student = new Student();
        student.setMustChangePassword(0);
        when(studentMapper.selectById("2021001")).thenReturn(student);

        assertFalse(cache.isRequired("student", "2021001"));
        verify(studentMapper).selectById("2021001");
    }

    @Test
    @DisplayName("evict 删除对应缓存键")
    void evictDeletesKey() {
        cache.evict("teacher", "T001");
        verify(redis).delete("pwd:must_change:teacher:T001");
    }

    @Test
    @DisplayName("未知角色一律视为无需强制改密")
    void unknownRoleIsNotRequired() {
        assertFalse(cache.isRequired("guest", "x"));
    }
}
