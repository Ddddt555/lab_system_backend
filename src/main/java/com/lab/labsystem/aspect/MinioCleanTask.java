package com.lab.labsystem.aspect;

import com.lab.labsystem.mapper.ExpProjectMapper;
import com.lab.labsystem.mapper.ExpReportMapper;
import com.lab.labsystem.service.MinioService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class MinioCleanTask {

    @Autowired private MinioService minioService;
    @Autowired private ExpReportMapper reportMapper;
    @Autowired private ExpProjectMapper projectMapper;
    
    // 🛑【核心新增】注入 Redisson 客户端
    @Autowired private RedissonClient redissonClient;

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanOrphanFiles() {
        // 🛑【核心新增】使用分布式锁，防止多实例并发执行
        RLock lock = redissonClient.getLock("lock:task:minio-clean");
        try {
            // 尝试获取锁：最多等待 0 秒，如果抢不到直接放弃本次执行；锁有效期 5 分钟（防止死锁）
            if (!lock.tryLock(0, 5, TimeUnit.MINUTES)) {
                System.out.println("⏳ [定时任务] 其他节点正在执行清理，当前节点跳过。");
                return;
            }

            System.out.println("🧹 [定时任务] 开始扫描 MinIO 孤儿文件...");
            // 1. 查出数据库中所有被引用的文件
            Set<String> dbFiles = new HashSet<>();
            List<String> reportFiles = reportMapper.selectAllFileUrls();
            List<String> guideFiles = projectMapper.selectAllGuideFiles();
            if (reportFiles != null) dbFiles.addAll(reportFiles);
            if (guideFiles != null) dbFiles.addAll(guideFiles);

            // 2. 查出 MinIO 中的所有文件
            List<String> minioFiles = minioService.listAllFiles();

            int deletedCount = 0;
            LocalDateTime safetyThreshold = LocalDateTime.now().minusHours(24); // 24小时安全线

            // 3. 找出差集并删除
            for (String minioFile : minioFiles) {
                if (!dbFiles.contains(minioFile)) {
                    LocalDateTime lastModified = minioService.getLastModifiedTime(minioFile);
                    if (lastModified.isBefore(safetyThreshold)) {
                        minioService.remove(minioFile);
                        deletedCount++;
                        System.out.println("🗑️ 已清理孤儿文件: " + minioFile + " (最后修改时间: " + lastModified + ")");
                    } else {
                        System.out.println("⏳ 跳过清理，可能是正在上传的文件: " + minioFile);
                    }
                }
            }
            System.out.println("✅ [定时任务] 清理完成，共清理 " + deletedCount + " 个孤儿文件。");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("❌ [定时任务] 被中断: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("❌ [定时任务] 清理失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 如果当前线程持有锁，才去释放
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}