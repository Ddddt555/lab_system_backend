# ==========================================
# 实验教学管理系统 - 一键恢复脚本
# ==========================================
param (
    [Parameter(Mandatory=$true)][string]$SqlFile,
    [Parameter(Mandatory=$false)][string]$MinioFile
)

$ErrorActionPreference = "Stop"
$backupDir = Join-Path $PSScriptRoot "..\backup"

if (!(Test-Path $SqlFile)) {
    $SqlFile = Join-Path $backupDir $SqlFile
}
if (!(Test-Path $SqlFile)) {
    Write-Host "❌ 找不到 SQL 备份文件: $SqlFile" -ForegroundColor Red
    exit 1
}

Write-Host "⚠️ 警告：即将覆盖现有数据库！按 Ctrl+C 取消，或按 Enter 继续..."
Read-Host

Write-Host "🔄 正在恢复 MySQL..."
Get-Content $SqlFile | docker exec -i labsystem-mysql-prod sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD"'
Write-Host "✅ MySQL 恢复完成！"

if ($MinioFile) {
    if (!(Test-Path $MinioFile)) {
        $MinioFile = Join-Path $backupDir $MinioFile
    }
    if (Test-Path $MinioFile) {
        Write-Host "🔄 正在恢复 MinIO 附件..."
        docker run --rm --volumes-from labsystem-minio-prod -v "${backupDir}:/backup" alpine sh -c "rm -rf /data/* && tar xzf /backup/$(Split-Path $MinioFile -Leaf) -C /"
        Write-Host "✅ MinIO 恢复完成！"
    } else {
        Write-Host "⚠️ 找不到 MinIO 备份文件，跳过附件恢复。"
    }
}
Write-Host "🎉 恢复流程执行完毕！"