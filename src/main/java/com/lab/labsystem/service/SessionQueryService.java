package com.lab.labsystem.service;

/**
 * 实验场次查询。
 *
 * <p>提供给端的场次列表，含教师姓名与项目名称/指导书的批量填充。
 * 目前由学生端使用，教师端后续可复用。
 */
public interface SessionQueryService {

    /**
     * 场次列表。
     *
     * @param page 页码，与 size 同时为空时返回列表而非分页对象
     * @param size 每页条数
     * @return {@code PageResult} 或 {@code List}
     */
    Object listSessions(Integer page, Integer size);
}
