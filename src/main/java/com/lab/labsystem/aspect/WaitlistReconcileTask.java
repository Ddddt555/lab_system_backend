package com.lab.labsystem.aspect;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lab.labsystem.entity.WaitlistRecord;
import com.lab.labsystem.mapper.WaitlistRecordMapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class WaitlistReconcileTask {

    @Autowired private WaitlistRecordMapper waitlistRecordMapper;
    @Autowired private StringRedisTemplate stringRedisTemplate;
    
    // 🛑【核心新增】注入 Redisson 客户端
    @Autowired private RedissonClient redissonClient;

    private static final String WAITLIST_QUEUE_PREFIX = "waitlist:queue:";
    private static final String WAITLIST_USER_PREFIX = "waitlist:user:";

    @Scheduled(cron = "0 */10 * * * ?")
    public void reconcileWaitlist() {
        // 🛑【核心新增】分布式锁
        RLock lock = redissonClient.getLock("lock:task:waitlist-reconcile");
        try {
            if (!lock.tryLock(0, 5, TimeUnit.MINUTES)) {
                log.info("⏳ [定时对账] 其他节点正在执行，当前节点跳过。");
                return;
            }

            log.info("⚖️ [定时对账] 开始核对候补队列数据...");
            
            List<WaitlistRecord> dbRecords = waitlistRecordMapper.selectList(
                    new QueryWrapper<WaitlistRecord>().eq("status", "WAITING")
            );

            for (WaitlistRecord record : dbRecords) {
                String sessionNo = record.getSessionNo();
                String studentNo = record.getStudentNo();
                
                String queueKey = WAITLIST_QUEUE_PREFIX + sessionNo;
                String userKey = WAITLIST_USER_PREFIX + sessionNo + ":" + studentNo;

                List<String> redisQueue = stringRedisTemplate.opsForList().range(queueKey, 0, -1);
                boolean inRedisQueue = redisQueue != null && redisQueue.contains(studentNo);
                boolean hasUserKey = Boolean.TRUE.equals(stringRedisTemplate.hasKey(userKey));

                if (!inRedisQueue) {
                    log.info("🔧 [定时对账] 发现丢失的候补人员，正在修复: {} (场次: {})", studentNo, sessionNo);
                    stringRedisTemplate.opsForList().rightPush(queueKey, studentNo);
                    stringRedisTemplate.opsForValue().set(userKey, "1");
                } else if (!hasUserKey) {
                    stringRedisTemplate.opsForValue().set(userKey, "1");
                }
            }

            log.info("✅ [定时对账] 核对完成！共检查 {} 名排队人员。", dbRecords.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("❌ [定时对账] 被中断: {}", e.getMessage());
        } catch (Exception e) {
            log.error("❌ [定时对账] 执行失败", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}