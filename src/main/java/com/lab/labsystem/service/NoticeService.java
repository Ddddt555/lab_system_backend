package com.lab.labsystem.service;

import java.util.Map;

/**
 * 通知业务。
 *
 * <p>从 {@code NoticeController} 中下沉出来的业务规则：
 * 发布校验（标题/内容非空、目标角色白名单、教师只能发学生通知）、
 * 按角色可见范围查询、未读统计与标记已读。
 */
public interface NoticeService {

    /**
     * 发布通知。
     *
     * @param title      标题
     * @param content    正文
     * @param targetRole 目标角色：all / student / teacher
     * @param role       发布人角色（来自 JWT）
     * @param userNo     发布人工号/账号（来自 JWT）
     * @return 成功提示
     */
    String publish(String title, String content, String targetRole, String role, String userNo);

    /**
     * 查询当前用户可见的通知列表与未读数。
     *
     * @param role   当前角色
     * @param userNo 当前用户
     * @return 包含 list 与 unreadCount 的结果
     */
    Map<String, Object> listForUser(String role, String userNo);

    /**
     * 将当前用户可见且未读的通知全部标记为已读。
     *
     * @return 本次新增的已读记录条数
     */
    int markAllRead(String role, String userNo);
}
