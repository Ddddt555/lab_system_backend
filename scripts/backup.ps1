# ==========================================
# 实验教学管理系统 - 一键备份脚本 (MySQL + MinIO)
# ==========================================
$ErrorActionPreference = "Stop"
$time = Get-Date -Format "yyyyMMdd_HHmmss"
$backupDir = Join-Path $PSScriptRoot "..\backup"

if (!(Test-Path $backupDir)) { 
    New-Item -ItemType Directory -Path $backupDir | Out-Null 
}

Write-Host "🔄 开始备份数据库..."
# 利用容器内的环境变量获取密码，直接导出，避免在宿主机暴露密码
docker exec labsystem-mysql-prod sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --databases lab_management' > "$backupDir\mysql_$time.sql"
Write-Host "✅ MySQL 备份完成: mysql_$time.sql"

Write-Host "🔄 开始备份 MinIO 附件..."
# 借助临时 alpine 容器，打包 MinIO 的挂载卷
docker run --rm --volumes-from labsystem-minio-prod -v "${backupDir}:/backup" alpine tar czf "/backup/minio_$time.tar.gz" /data
Write-Host "✅ MinIO 备份完成: minio_$time.tar.gz"

Write-Host "🧹 清理 7 天前的旧备份..."
$limit = (Get-Date).AddDays(-7)
Get-ChildItem -Path $backupDir -Filter "mysql_*.sql" | Where-Object { $_.LastWriteTime -lt $limit } | Remove-Item -Force
Get-ChildItem -Path $backupDir -Filter "minio_*.tar.gz" | Where-Object { $_.LastWriteTime -lt $limit } | Remove-Item -Force

Write-Host "🎉 所有备份任务执行完毕！文件保存在 $backupDir"