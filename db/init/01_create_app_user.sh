#!/bin/bash
set -e

echo "🛠️ 正在创建应用专用数据库账号: ${DB_USERNAME}..."

mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
    CREATE USER IF NOT EXISTS '${DB_USERNAME}'@'%' IDENTIFIED BY '${DB_APP_PASSWORD}';
    GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES ON lab_management.* TO '${DB_USERNAME}'@'%';
    FLUSH PRIVILEGES;
EOSQL

echo "✅ 账号 ${DB_USERNAME} 创建与授权完成！"
