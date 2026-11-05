cd /g/DevEnv/Project/labsystem/labsystem/scripts
cat > backup.sh <<'EOF'
#!/bin/bash
set -e

# ==========================================
# 实验教学管理系统 -一键备份脚本
# 支持：MySQL 数据导出 + MinIO 附件打包
# ==========================================

# 备份路径
BACKUP_DIR="/backup"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
MYSQL_CONTAINER="labsystem-mysql-prod"
MINIO_CONTAINER="labsystem-minio-prod"

echo "🔄 [$(date +"%Y-%m-%d %H:%M:%S")] 开始备份..."

# 1. 备份 MySQL
echo "⏳ 正在备份 MySQL 数据库..."
# 利用容器内的环境变量获取密码，避免在宿主机暴露密码，并保证字符集正确
docker exec $MYSQL_CONTAINER sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --databases lab_management' > "$BACKUP_DIR/mysql_$TIMESTAMP.sql"

if [ -s "$BACKUP_DIR/mysql_$TIMESTAMP.sql" ]; then
    echo "✅ MySQL 备份完成: mysql_$TIMESTAMP.sql"
else
    echo "❌ MySQL 备份失败！文件为空。"
    exit 1
fi

# 2. 备份 MinIO 数据卷
echo "⏳ 正在备份 MinIO 附件..."
docker run --rm --volumes-from $MINIO_CONTAINER -v "$BACKUP_DIR:/backup" alpine tar czf "/backup/minio_$TIMESTAMP.tar.gz" /data
echo "✅ MinIO 备份完成: minio_$TIMESTAMP.tar.gz"

# 3. 清理 7 天前的旧备份
echo "🧹 清理 7 天前的旧备份..."
find "$BACKUP_DIR" -name "mysql_*.sql" -type f -mtime +7 -exec rm {} \;
find "$BACKUP_DIR" -name "minio_*.tar.gz" -type f -mtime +7 -exec rm {} \;

echo "🎉 [$(date +"%Y-%m-%d %H:%M:%S")] 所有备份任务执行完毕！"
EOF

# 赋权
chmod +x backup.sh