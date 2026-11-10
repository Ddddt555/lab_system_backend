package com.lab.labsystem.aspect;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lab.labsystem.entity.SysLog;
import com.lab.labsystem.mapper.SysLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 系统操作日志的保留期清理。
 *
 * <p>{@code sys_log} 只增不减：每次带 {@code @LogRecord} 的操作都会写一行。
 * 长期运行后表会膨胀到百万级，即使日志查询接口已经分页，
 * COUNT 和深分页也会明显变慢。
 *
 * <p>这里按天删除超过保留期（默认 180 天）的记录。
 * 清理依赖 {@code idx_create_time} 索引，避免全表扫描。
 */
@Slf4j
@Component
public class SysLogCleanTask {

    private static final String LOCK_KEY = "lock:task:sys-log-clean";

    @Autowired private SysLogMapper logMapper;
    @Autowired private RedissonClient redissonClient;

    /** 日志保留天数，可通过 LOG_RETENTION_DAYS 覆盖 */
    @Value("${app.log.retention-days:180}")
    private int retentionDays;

    /** 每天凌晨 03:30 执行，避开白天的业务高峰 */
    @Scheduled(cron = "0 30 3 * * ?")
    public void cleanExpiredLogs() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        boolean locked = false;
        try {
            // 抢不到锁说明其他节点正在清理，直接跳过本次
            locked = lock.tryLock(0, 10, TimeUnit.MINUTES);
            if (!locked) {
                log.info("⏳ [定时任务] 其他节点正在清理系统日志，当前节点跳过。");
                return;
            }

            if (retentionDays <= 0) {
                log.warn("⚠️ [定时任务] 日志保留天数配置非法（{}），跳过本次清理。", retentionDays);
                return;
            }

            LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
            int deleted = logMapper.delete(
                    new QueryWrapper<SysLog>().lt("create_time", cutoff));

            log.info("🧹 [定时任务] 系统日志清理完成：保留 {} 天，删除 {} 条（截止 {}）。",
                    retentionDays, deleted, cutoff);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("❌ [定时任务] 日志清理被中断: {}", e.getMessage());
        } catch (Exception e) {
            // 清理失败不能影响主业务，仅记录
            log.error("❌ [定时任务] 日志清理失败", e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
