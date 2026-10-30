package com.lab.labsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lab.labsystem.controller.GlobalWebSocket;
import com.lab.labsystem.entity.*;
import com.lab.labsystem.mapper.*;
import com.lab.labsystem.service.StudentBookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class StudentBookingServiceImpl implements StudentBookingService {

    @Autowired private ExpSessionMapper sessionMapper;
    @Autowired private ExpProjectMapper projectMapper;
    @Autowired private StudentTeachClassMapper studentTeachClassMapper;
    @Autowired private StudentBookingMapper bookingMapper;
    @Autowired private ExpReportMapper reportMapper;
    @Autowired private WaitlistRecordMapper waitlistRecordMapper;
    @Autowired private StringRedisTemplate stringRedisTemplate;

    // 候补队列在 Redis 中的 Key 前缀
    private static final String WAITLIST_QUEUE_PREFIX = "waitlist:queue:";
    // 候补人在 Redis 中的标记（防止重复排队）
    private static final String WAITLIST_USER_PREFIX = "waitlist:user:";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String bookSession(String studentNo, String sessionNo) {
        // 把核心预约逻辑抽成一个私有方法，方便候补自动补位时复用
        return doBookSession(studentNo, sessionNo, false);
    }

    /**
     * 核心预约逻辑
     * @param isWaitlistAutoBook 是否为候补自动补位触发的预约
     */
    private String doBookSession(String studentNo, String sessionNo, boolean isWaitlistAutoBook) {
        ExpSession targetSession = sessionMapper.selectById(sessionNo);
        if (targetSession == null) throw new RuntimeException("该场次不存在！");

        ExpProject project = projectMapper.selectById(targetSession.getProjectNo());
        if (project != null && project.getClassNo() != null) {
            QueryWrapper<StudentTeachClass> scQw = new QueryWrapper<StudentTeachClass>()
                    .eq("student_no", studentNo).eq("class_no", project.getClassNo());
            if (studentTeachClassMapper.selectCount(scQw) == 0) {
                throw new RuntimeException("你尚未加入该实验所属的教学班，请先去【选课中心】加入！");
            }
        }

        QueryWrapper<StudentBooking> qw = new QueryWrapper<>();
        qw.eq("student_no", studentNo).eq("status", 1);
        List<StudentBooking> myBookings = bookingMapper.selectList(qw);

        for (StudentBooking b : myBookings) {
            ExpSession existingSession = sessionMapper.selectById(b.getSessionNo());
            if (existingSession != null) {
                if (existingSession.getSessionNo().equals(sessionNo)) {
                    throw new RuntimeException("你已经预约过该场次了，请勿重复预约！");
                }
                if (existingSession.getSemester().equals(targetSession.getSemester()) &&
                    existingSession.getWeekNum().equals(targetSession.getWeekNum()) &&
                    existingSession.getDayOfWeek().equals(targetSession.getDayOfWeek()) &&
                    existingSession.getPeriod().equals(targetSession.getPeriod())) {
                    throw new RuntimeException("时间冲突！你在【" + existingSession.getSemester() + " 第" + existingSession.getWeekNum() + "周】已经预约了其他实验课！");
                }
            }
        }

        UpdateWrapper<ExpSession> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("session_no", sessionNo).gt("remaining_seats", 0).setSql("remaining_seats = remaining_seats - 1");
        int rows = sessionMapper.update(null, updateWrapper);
        if (rows == 0) throw new RuntimeException("手慢了，该场次已被约满！");

        StudentBooking booking = new StudentBooking();
        booking.setBookingNo("B" + UUID.randomUUID().toString().substring(0, 8));
        booking.setStudentNo(studentNo);
        booking.setSessionNo(sessionNo);
        booking.setBookingTime(LocalDateTime.now());
        booking.setStatus(1);
        bookingMapper.insert(booking);

        ExpSession updatedSession = sessionMapper.selectById(sessionNo);
        if (updatedSession != null) {
            GlobalWebSocket.broadcastToRole("all", 
                "{\"type\":\"SEAT_UPDATE\",\"sessionNo\":\"" + sessionNo + "\",\"seats\":" + updatedSession.getRemainingSeats() + "}");
        }

        // 如果这次预约是候补自动补位触发的，点对点通知该学生
        if (isWaitlistAutoBook) {
            int sent = GlobalWebSocket.sendToUser(studentNo,
                "{\"type\":\"WAITLIST_SUCCESS\",\"message\":\"您候补的实验场次已成功为您预约！\"}");
            // sent == 0 表示学生当前不在线，消息未送达。
            if (sent == 0) {
                System.err.println("[候补通知] 学生 " + studentNo + " 当前不在线，WAITLIST_SUCCESS 未推送");
            }
        }

        return "预约成功！";
    }

    @Override
    public String joinWaitlist(String studentNo, String sessionNo) {
        ExpSession session = sessionMapper.selectById(sessionNo);
        if (session == null) throw new RuntimeException("场次不存在！");
        
        if (session.getRemainingSeats() > 0) {
            throw new RuntimeException("当前场次还有余量，无需排队，请直接预约！");
        }

        // 1. 检查是否已经预约了该场次
        Long booked = bookingMapper.selectCount(new QueryWrapper<StudentBooking>()
                .eq("student_no", studentNo).eq("session_no", sessionNo).eq("status", 1));
        if (booked > 0) throw new RuntimeException("您已预约该场次，无需排队！");

        // 2. 检查是否已经在候补队列中 (用 Redis Set 防重复)
        String userKey = WAITLIST_USER_PREFIX + sessionNo + ":" + studentNo;
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(userKey))) {
            throw new RuntimeException("您已经在候补队列中，请耐心等待！");
        }

        // 3. MySQL 层兜底清理
        waitlistRecordMapper.delete(new QueryWrapper<WaitlistRecord>()
                .eq("session_no", sessionNo).eq("student_no", studentNo));

        // 4. 写入 MySQL 记录
        WaitlistRecord record = new WaitlistRecord();
        record.setSessionNo(sessionNo);
        record.setStudentNo(studentNo);
        record.setCreateTime(LocalDateTime.now());
        waitlistRecordMapper.insert(record);

        // 5. 写入 Redis 队列 (右边进，左边出)
        String queueKey = WAITLIST_QUEUE_PREFIX + sessionNo;
        stringRedisTemplate.opsForList().rightPush(queueKey, studentNo);
        
        // 6. 设置防重复标记
        stringRedisTemplate.opsForValue().set(userKey, "1");

        return "已成功加入候补队列，一旦有人退课，系统将为您自动预约！";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String cancelBooking(String studentNo, String bookingNo) {
        StudentBooking booking = bookingMapper.selectById(bookingNo);
        if (booking == null) throw new RuntimeException("预约不存在或已取消！");
        if (!booking.getStudentNo().equals(studentNo)) throw new RuntimeException("非法操作：你不能取消别人的预约！");

        ExpSession session = sessionMapper.selectById(booking.getSessionNo());
        if (session != null) {
            QueryWrapper<ExpReport> reportQw = new QueryWrapper<>();
            reportQw.eq("student_no", studentNo).eq("project_no", session.getProjectNo()).eq("is_latest", 1).last("limit 1");
            ExpReport report = reportMapper.selectOne(reportQw);
            if (report != null && report.getScore() != null) {
                throw new RuntimeException("该实验报告已被教师批阅，无法取消预约！");
            }
        }

        bookingMapper.deleteById(bookingNo);
        
        if (session != null) {
            // ⚠️ 步骤 5：核心修改，改为数据库原子自增，且防止余量超过总容量
        	UpdateWrapper<ExpSession> updateWrapper = new UpdateWrapper<>();
        	updateWrapper.eq("session_no", session.getSessionNo())
        	             .apply("remaining_seats < capacity") // ✅ 用 apply 直接写 SQL 条件
        	             .setSql("remaining_seats = remaining_seats + 1");
        	sessionMapper.update(null, updateWrapper);
            // 重新查询最新的场次信息，用于 WebSocket 广播
            ExpSession updatedSession = sessionMapper.selectById(session.getSessionNo());
            if (updatedSession != null) {
                // ⚠️ 广播最新的余量
                GlobalWebSocket.broadcastToRole("all", 
                    "{\"type\":\"SEAT_UPDATE\",\"sessionNo\":\"" + updatedSession.getSessionNo() + "\",\"seats\":" + updatedSession.getRemainingSeats() + "}");
            }

            // ⚠️ 核心：触发候补补位
            handleWaitlist(session.getSessionNo());
        }
        return "取消预约成功！";
    }

    /**
     * 处理候补逻辑（被退课操作触发）
     */
    private void handleWaitlist(String sessionNo) {
        String queueKey = WAITLIST_QUEUE_PREFIX + sessionNo;
        
        // 循环取人（防止队列里有人因为时间冲突等原因补位失败）
        while (true) {
            // 从左边弹出一个学生
            String nextStudentNo = stringRedisTemplate.opsForList().leftPop(queueKey);
            if (nextStudentNo == null) {
                break; // 没人排队了，结束
            }

            // 删除该学生的防重复标记
            String userKey = WAITLIST_USER_PREFIX + sessionNo + ":" + nextStudentNo;
            stringRedisTemplate.delete(userKey);

            try {
                // 尝试帮他预约
                doBookSession(nextStudentNo, sessionNo, true);
                // 清理 MySQL 里的候补记录 (由于去掉了 @TableLogic，现在会自动物理删除)
                waitlistRecordMapper.delete(new QueryWrapper<WaitlistRecord>()
                        .eq("session_no", sessionNo).eq("student_no", nextStudentNo));
                
                // 补位成功，直接退出循环，因为余量又变成 0 了
                break; 
            } catch (Exception e) {
                // 补位失败（比如他此时已经约了别的时间冲突的课），跳过这个人，循环继续找下一个
                System.err.println("候补自动预约失败: " + nextStudentNo + "，原因: " + e.getMessage());
            }
        }
    }
}