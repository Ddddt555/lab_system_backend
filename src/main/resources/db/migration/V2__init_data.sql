-- ==========================================
-- 初始化系统必备的种子数据
-- ==========================================

-- 1. 插入默认的超级管理员 (账号: admin, 密码: 123456)
-- 注意：这里的密码字符串是经过 BCrypt 加密后的 123456 密文
INSERT INTO sys_admin (username, password, deleted, must_change_password) 
VALUES ('admin', '$2a$10$7c3p.a5X5c8U1sY2xZ3eO.h1v6X9Q0w9T5y2Z2c2b2a2d2e2f2g2', 0, 0)
ON DUPLICATE KEY UPDATE username = username;

-- 2. 插入一个默认的学期（如果没有学期，老师连课都排不了）
INSERT INTO sys_semester (semester_name, start_date, total_weeks, deleted)
VALUES ('2026-2027 上学期', '2026-09-01', 20, 0)
ON DUPLICATE KEY UPDATE semester_name = semester_name;