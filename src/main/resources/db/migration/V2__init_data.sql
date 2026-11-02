-- 1. 插入默认的超级管理员 (账号: admin, 密码: 123456)
INSERT INTO sys_admin (username, password, deleted, must_change_password) 
VALUES ('admin', '$2a$10$pFJRdmD8IYgCM7GUEoq2uuthGmUb9svvXHI82D7yS/3cSO23rMMWG', 0, 0)
ON DUPLICATE KEY UPDATE username = username;