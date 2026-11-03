# 数据库初始化与安全记录

## 1. 应用连接账号
系统在后端配置中使用了专用的 `lab_app` 账号（非 root），以保证最小权限原则。
如果在全新的 MySQL 中部署，需要先执行以下 SQL 创建该账号：

```sql
CREATE USER IF NOT EXISTS 'lab_app'@'%' IDENTIFIED BY 'LabApp@Secure2026!';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON lab_management.* TO 'lab_app'@'%';
FLUSH PRIVILEGES;
2. 自动化测试库
自动化集成测试会连接 lab_management_test，建表脚本和假数据脚本分别在 src/main/resources/db/migration/ 和根目录的 run_test_data.sql 中。