package com.lab.labsystem.aspect;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lab.labsystem.entity.WaitlistRecord;
import com.lab.labsystem.mapper.WaitlistRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class WaitlistReconcileTask {

    @Autowired private WaitlistRecordMapper waitlistRecordMapper;
    @Autowired private StringRedisTemplate stringRedisTemplate;

    private static final String WAITLIST_QUEUE_PREFIX = "waitlist:queue:";
    private static final String WAITLIST_USER_PREFIX = "waitlist:user:";

    /**
     * 🛑【定时对账任务】每 10 分钟执行一次
     * 修复 Redis 与 MySQL 之间由于网络抖动或宕机导致的数据不一致
     */
    @Scheduled(cron = "0 */10 * * * ?")
    public void reconcileWaitlist() {
        System.out.println("⚖️ [定时对账] 开始核对候补队列数据...");
        
        try {
            // 1. 查出 MySQL 中所有正在排队（WAITING）的记录
            List<WaitlistRecord> dbRecords = waitlistRecordMapper.selectList(
                    new QueryWrapper<WaitlistRecord>().eq("status", "WAITING")
            );

            for (WaitlistRecord record : dbRecords) {
                String sessionNo = record.getSessionNo();
                String studentNo = record.getStudentNo();
                
                String queueKey = WAITLIST_QUEUE_PREFIX + sessionNo;
                String userKey = WAITLIST_USER_PREFIX + sessionNo + ":" + studentNo;

                // 2. 检查 Redis 里有没有这个人（检查范围 list 和防重复标记 key）
                List<String> redisQueue = stringRedisTemplate.opsForList().range(queueKey, 0, -1);
                boolean inRedisQueue = redisQueue != null && redisQueue.contains(studentNo);
                boolean hasUserKey = Boolean.TRUE.equals(stringRedisTemplate.hasKey(userKey));

                // 3. 修复逻辑：如果 MySQL 有，但 Redis 丢了
                if (!inRedisQueue) {
                    System.out.println("🔧 [定时对账] 发现丢失的候补人员，正在修复: " + studentNo + " (场次: " + sessionNo + ")");
                    // 重新放回队列尾部（防止并发冲突，加在尾部是最安全的）
                    stringRedisTemplate.opsForList().rightPush(queueKey, studentNo);
                    stringRedisTemplate.opsForValue().set(userKey, "1");
                } else if (!hasUserKey) {
                    // 队列里有，但防重复标记没了，补上
                    stringRedisTemplate.opsForValue().set(userKey, "1");
                }
            }

            System.out.println("✅ [定时对账] 核对完成！共检查 " + dbRecords.size() + " 名排队人员。");
        } catch (Exception e) {
            System.err.println("❌ [定时对账] 执行失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}