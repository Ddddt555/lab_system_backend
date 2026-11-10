package com.lab.labsystem.service.impl;

import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.entity.TeachClass;
import com.lab.labsystem.mapper.TeachClassMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 成绩权重修改的校验测试。
 *
 * <p>这些规则原先抛 RuntimeException，会被异常处理器当作系统错误脱敏，
 * 用户只能看到"系统繁忙"。改为 BusinessException 后消息应能直达前端，
 * 这里锁住类型与文案。
 */
class TeacherScoreServiceWeightTest {

    private TeachClassMapper teachClassMapper;
    private TeacherScoreServiceImpl service;

    @BeforeEach
    void setUp() {
        teachClassMapper = mock(TeachClassMapper.class);
        service = new TeacherScoreServiceImpl();
        ReflectionTestUtils.setField(service, "teachClassMapper", teachClassMapper);
    }

    @Test
    @DisplayName("权重为空或负数时以业务异常返回")
    void rejectInvalidWeight() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.updateWeight("T001", "C1", null, 70));
        assertEquals("权重不能为空或负数！", e.getMessage());

        BusinessException e2 = assertThrows(BusinessException.class,
                () -> service.updateWeight("T001", "C1", -10, 110));
        assertEquals("权重不能为空或负数！", e2.getMessage());
    }

    @Test
    @DisplayName("权重之和不为 100 时以业务异常返回")
    void rejectWeightSum() {
        BusinessException e = assertThrows(BusinessException.class,
                () -> service.updateWeight("T001", "C1", 30, 60));
        assertEquals("权重之和必须等于 100%！", e.getMessage());
    }

    @Test
    @DisplayName("非授课教师修改权重时以业务异常返回")
    void rejectOtherTeacher() {
        TeachClass tc = new TeachClass();
        tc.setClassNo("C1");
        tc.setWorkNo("T999");
        when(teachClassMapper.selectById("C1")).thenReturn(tc);

        BusinessException e = assertThrows(BusinessException.class,
                () -> service.updateWeight("T001", "C1", 30, 70));
        assertEquals("非法操作：您无权修改该班级权重！", e.getMessage());
    }
}
