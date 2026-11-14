package com.lab.labsystem.service.student;

/**
 * 实验场次查询。
 * <p>提供给端的场次列表，含教师姓名与项目名称/指导书的批量填充。</p>
 */
public interface SessionQueryService {
    /**
     * 场次列表。
     *
     * @param studentNo 当前登录的学生学号
     * @param page      页码，与 size 同时为空时返回列表而非分页对象
     * @param size      每页条数
     * @return {@code PageResult} 或 {@code List}
     */
    Object listSessions(String studentNo, Integer page, Integer size);
}