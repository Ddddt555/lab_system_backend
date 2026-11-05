# ==========================================
# 实验教学管理系统 - 一键备份脚本 (Windows 版)
# ==========================================
$ErrorActionPreference = "Stop"
$time = Get-Date -Format "yyyyMMdd_HHmmss"
# 自动定位到项目根目录的 backup 文件夹
$backupDir = Resolve-Path (Join-Path $PSScriptRoot "..\backup") -ErrorAction SilentlyContinue
if (!$backupDir) {
    $backupDir = Join-Path $PSScriptRoot "..\backup"
    New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
}

$mysqlContainer = "labsystem-mysql-prod"
$minioContainer = "labsystem-minio-prod"

Write-Host "🔄 [$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] 开始备份..." -ForegroundColor Cyan

# 1. 备份 MySQL
Write-Host "⏳ 正在备份 MySQL 数据库..."
$sqlFile = Join-Path $backupDir "mysql_$time.sql"
# 加入 --single-transaction 防止锁表，加入 --default-character-set=utf8mb4 防止中文乱码
docker exec $mysqlContainer sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --default-character-set=utf8mb4 --databases lab_management' > $sqlFile

if ((Test-Path $sqlFile) -and (Get-Item $sqlFile).Length -gt 0) {
    Write-Host "✅ MySQL 备份完成: mysql_$time.sql" -ForegroundColor Green
} else {
    Write-Host "❌ MySQL 备份失败！文件为空。请检查容器是否在运行。" -ForegroundColor Red
    exit 1
}

# 2. 备份 MinIO
Write-Host "⏳ 正在备份 MinIO 附件..."
docker run --rm --volumes-from $minioContainer -v "${backupDir}:/backup" alpine tar czf "/backup/minio_$time.tar.gz" /data
Write-Host "✅ MinIO 备份完成: minio_$time.tar.gz" -ForegroundColor Green

# 3. 清理 7 天前的旧备份
Write-Host "🧹 清理 7 天前的旧备份..."
$limit = (Get-Date).AddDays(-7)
Get-ChildItem -Path $backupDir -Filter "mysql_*.sql" | Where-Object { $_.LastWriteTime -lt $limit } | Remove-Item -Force
Get-ChildItem -Path $backupDir -Filter "minio_*.tar.gz" | Where-Object { $_.LastWriteTime -lt $limit } | Remove-Item -Force

Write-Host "🎉 [$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] 所有备份任务执行完毕！文件保存在 $backupDir" -ForegroundColor Cyan