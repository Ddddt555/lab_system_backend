package com.lab.labsystem.aspect;

import com.lab.labsystem.mapper.ExpProjectMapper;
import com.lab.labsystem.mapper.ExpReportMapper;
import com.lab.labsystem.service.MinioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class MinioCleanTask {

    @Autowired private MinioService minioService;
    @Autowired private ExpReportMapper reportMapper;
    @Autowired private ExpProjectMapper projectMapper;

    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanOrphanFiles() {
        System.out.println("🧹 [定时任务] 开始扫描 MinIO 孤儿文件...");
        try {
            // 1. 查出数据库中所有被引用的文件
            Set<String> dbFiles = new HashSet<>();
            List<String> reportFiles = reportMapper.selectAllFileUrls();
            List<String> guideFiles = projectMapper.selectAllGuideFiles();
            if (reportFiles != null) dbFiles.addAll(reportFiles);
            if (guideFiles != null) dbFiles.addAll(guideFiles);

            // 2. 查出 MinIO 中的所有文件
            List<String> minioFiles = minioService.listAllFiles();

            int deletedCount = 0;
            LocalDateTime safetyThreshold = LocalDateTime.now().minusHours(24); // 🛑 24小时安全线

            // 3. 找出差集并删除
            for (String minioFile : minioFiles) {
                // 如果 MinIO 里有，但数据库里没有记录
                if (!dbFiles.contains(minioFile)) {
                    // 🛑【核心修复】检查文件最后修改时间，必须早于 24 小时才允许删除！
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
        } catch (Exception e) {
            System.err.println("❌ [定时任务] 清理失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}