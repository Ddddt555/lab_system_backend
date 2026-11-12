package com.lab.labsystem.service;

import com.lab.labsystem.dto.request.SessionCreateDTO;
import com.lab.labsystem.vo.StudentVO;

import java.util.List;

/**
 * 教师端场次管理。
 *
 * <p>从 {@code TeacherController} 下沉出来的：排课（含三重冲突校验与绝对时间换算）、
 * 我负责的场次列表、场次预约名单、取消排课。
 */
public interface TeacherSessionService {

    /**
     * 安排实验场次。
     *
     * <p>校验链：项目归属 → 实验室存在与容量 → 三重冲突（教师/实验室/教学班）。
     *
     * @param dto 包含项目、实验室、学期、周次、节次等排课参数
     * @param workNo 当前操作的教师工号
     * @return 成功提示（含生成的场次号）
     * @throws com.lab.labsystem.common.BusinessException 任一步校验失败
     */
    String addSession(SessionCreateDTO dto, String workNo);

    /**
     * 当前教师负责的场次列表（按时间升序）。
     *
     * @return {@code PageResult} 或 {@code List}
     */
    Object listMySessions(String workNo, Integer page, Integer size);

    /**
     * 查看某场次的预约学生名单。
     *
     * @throws com.lab.labsystem.common.BusinessException 场次不存在或不属于当前教师
     */
    List<StudentVO> listSessionBookings(String sessionNo, String workNo);

    /**
     * 取消排课。
     *
     * @throws com.lab.labsystem.common.BusinessException 场次不存在或不属于当前教师
     */
    String deleteSession(String sessionNo, String workNo);
}