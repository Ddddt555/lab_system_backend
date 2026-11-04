-- 创建通知已读记录表
CREATE TABLE `sys_notice_read` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `notice_id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知ID',
  `user_no` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '阅读人账号(学号/工号/admin)',
  `read_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '阅读时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notice_user` (`notice_id`,`user_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知已读记录表';