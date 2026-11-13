package com.lab.labsystem.service.student.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.ErrorCode;
import com.lab.labsystem.common.PageResult;
import com.lab.labsystem.common.QueryLimits;
import com.lab.labsystem.entity.*;
import com.lab.labsystem.mapper.*;
import com.lab.labsystem.service.student.StudentBookingService;
import com.lab.labsystem.vo.SessionVO;
import com.lab.labsystem.websocket.GlobalWebSocket;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StudentBookingServiceImpl implements StudentBookingService {

    @Autowired private ExpSessionMapper sessionMapper;
    @Autowired private ExpProjectMapper projectMapper;
    @Autowired private StudentTeachClassMapper studentTeachClassMapper;
    @Autowired private StudentBookingMapper bookingMapper;
    @Autowired private ExpReportMapper reportMapper;
    @Autowired private WaitlistRecordMapper waitlistRecordMapper;
    @Autowired private StringRedisTemplate stringRedisTemplate;
    @Autowired private TeacherMapper teacherMapper;
    @Autowired private RedissonClient redissonClient;

    // 候补队列在 Redis 中的 Key 前缀
    private static final String WAITLIST_QUEUE_PREFIX = "waitlist:queue:";
    // 候补人在 Redis 中的标记（防止重复排队）
    private static final String WAITLIST_USER_PREFIX = "waitlist:user:";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String bookSession(String studentNo, String sessionNo) {
        String lockKey = "lock:book:" + studentNo + ":" + sessionNo;
        RLock lock = redissonClient.getLock(lockKey);

        boolean isLocked = false;
        try {
            isLocked = lock.tryLock(0, -1, TimeUnit.SECONDS);
            if (!isLocked) {
                throw BusinessException.of(ErrorCode.CONCURRENT_CONFLICT, "您正在预约中，请勿频繁点击！");
            }
            
            return doBookSession(studentNo, sessionNo, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BusinessException.of(ErrorCode.SYSTEM_ERROR, "系统繁忙，请稍后重试！");
        } finally {
            if (isLocked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 核心预约逻辑
     * @param isWaitlistAutoBook 是否为候补自动补位触发的预约
     */
    private String doBookSession(String studentNo, String sessionNo, boolean isWaitlistAutoBook) {
        ExpSession targetSession = sessionMapper.selectById(sessionNo);
        if (targetSession == null) throw BusinessException.of(ErrorCode.DATA_NOT_FOUND, "该场次不存在！");

        ExpProject project = projectMapper.selectById(targetSession.getProjectNo());
        if (project != null && project.getClassNo() != null) {
            QueryWrapper<StudentTeachClass> scQw = new QueryWrapper<StudentTeachClass>()
                    .eq("student_no", studentNo).eq("class_no", project.getClassNo());
            if (studentTeachClassMapper.selectCount(scQw) == 0) {
                throw BusinessException.of(ErrorCode.BAD_REQUEST, "你尚未加入该实验所属的教学班，请先去【选课中心】加入！");
            }
        }

        QueryWrapper<StudentBooking> qw = new QueryWrapper<>();
        qw.eq("student_no", studentNo).eq("status", 1);
        List<StudentBooking> myBookings = bookingMapper.selectList(qw);

        for (StudentBooking b : myBookings) {
            ExpSession existingSession = sessionMapper.selectById(b.getSessionNo());
            if (existingSession != null) {
                if (existingSession.getSessionNo().equals(sessionNo)) {
                    throw BusinessException.of(ErrorCode.BAD_REQUEST, "你已经预约过该场次了，请勿重复预约！");
                }
                if (existingSession.getSemester().equals(targetSession.getSemester()) &&
                    existingSession.getWeekNum().equals(targetSession.getWeekNum()) &&
                    existingSession.getDayOfWeek().equals(targetSession.getDayOfWeek()) &&
                    existingSession.getPeriod().equals(targetSession.getPeriod())) {
                    throw BusinessException.of(ErrorCode.BAD_REQUEST, "时间冲突！你在【" + existingSession.getSemester() + " 第" + existingSession.getWeekNum() + "周】已经预约了其他实验课！");
                }
            }
        }

        UpdateWrapper<ExpSession> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("session_no", sessionNo).gt("remaining_seats", 0).setSql("remaining_seats = remaining_seats - 1");
        int rows = sessionMapper.update(null, updateWrapper);
        if (rows == 0) throw BusinessException.of(ErrorCode.BAD_REQUEST, "手慢了，该场次已被约满！");

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

        if (isWaitlistAutoBook) {
            int sent = GlobalWebSocket.sendToUser(studentNo,
                "{\"type\":\"WAITLIST_SUCCESS\",\"message\":\"您候补的实验场次已成功为您预约！\"}");
            if (sent == 0) {
                log.debug("[候补通知] 学生 {} 当前不在线，WAITLIST_SUCCESS 未推送", studentNo);
            }
        }

        return "预约成功！";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String joinWaitlist(String studentNo, String sessionNo) {
        ExpSession session = sessionMapper.selectById(sessionNo);
        if (session == null) throw BusinessException.of(ErrorCode.DATA_NOT_FOUND, "场次不存在！");
        
        if (session.getRemainingSeats() > 0) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "当前场次还有余量，无需排队，请直接预约！");
        }

        Long booked = bookingMapper.selectCount(new QueryWrapper<StudentBooking>()
                .eq("student_no", studentNo).eq("session_no", sessionNo).eq("status", 1));
        if (booked > 0) throw BusinessException.of(ErrorCode.BAD_REQUEST, "您已预约该场次，无需排队！");

        String userKey = WAITLIST_USER_PREFIX + sessionNo + ":" + studentNo;
        
        // 使用 setIfAbsent 代替 hasKey + set，保证并发防重的原子性
        Boolean isAbsent = stringRedisTemplate.opsForValue().setIfAbsent(userKey, "1", Duration.ofDays(1));
        if (!Boolean.TRUE.equals(isAbsent)) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "您已经在候补队列中，请耐心等待！");
        }

        try {
            // 清理历史废弃记录
            waitlistRecordMapper.delete(new QueryWrapper<WaitlistRecord>()
                    .eq("session_no", sessionNo).eq("student_no", studentNo));

            // 写入 MySQL
            WaitlistRecord record = new WaitlistRecord();
            record.setSessionNo(sessionNo);
            record.setStudentNo(studentNo);
            record.setCreateTime(LocalDateTime.now());
            record.setStatus("WAITING");
            waitlistRecordMapper.insert(record);
        } catch (Exception e) {
            // 如果数据库写入失败（比如唯一键冲突），把刚才在 Redis 占的坑释放掉，保持数据一致
            stringRedisTemplate.delete(userKey);
            throw e;
        }

        // 注册事务同步器，只有在 MySQL 事务成功 Commit 之后，才去写 Redis 队列
        // 避免 MySQL 回滚导致 Redis 残留幽灵数据
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        String queueKey = WAITLIST_QUEUE_PREFIX + sessionNo;
                        stringRedisTemplate.opsForList().rightPush(queueKey, studentNo);
                    } catch (Exception e) {
                        // 如果这里写 Redis 失败，数据库已经提交。
                        // 依赖 WaitlistReconcileTask 定时对账任务自动补偿
                        log.error("加入候补队列写入 Redis 失败，等待定时任务补偿", e);
                    }
                }
            }
        );

        return "已成功加入候补队列，一旦有人退课，系统将为您自动预约！";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String cancelBooking(String studentNo, String bookingNo) {
        StudentBooking booking = bookingMapper.selectById(bookingNo);
        if (booking == null) throw BusinessException.of(ErrorCode.DATA_NOT_FOUND, "预约不存在或已取消！");
        if (!booking.getStudentNo().equals(studentNo)) throw BusinessException.of(ErrorCode.FORBIDDEN, "非法操作：你不能取消别人的预约！");

        ExpSession session = sessionMapper.selectById(booking.getSessionNo());
        if (session != null) {
            QueryWrapper<ExpReport> reportQw = new QueryWrapper<>();
            reportQw.eq("student_no", studentNo).eq("project_no", session.getProjectNo()).eq("is_latest", 1).last("limit 1");
            ExpReport report = reportMapper.selectOne(reportQw);
            if (report != null && report.getScore() != null) {
                throw BusinessException.of(ErrorCode.BAD_REQUEST, "该实验报告已被教师批阅，无法取消预约！");
            }
        }

        bookingMapper.deleteById(bookingNo);
        
        if (session != null) {
            UpdateWrapper<ExpSession> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("session_no", session.getSessionNo())
                        .apply("remaining_seats < capacity")
                        .setSql("remaining_seats = remaining_seats + 1");
            sessionMapper.update(null, updateWrapper);

            ExpSession updatedSession = sessionMapper.selectById(session.getSessionNo());
            if (updatedSession != null) {
                GlobalWebSocket.broadcastToRole("all", 
                    "{\"type\":\"SEAT_UPDATE\",\"sessionNo\":\"" + updatedSession.getSessionNo() + "\",\"seats\":" + updatedSession.getRemainingSeats() + "}");
            }

            // 触发候补补位
            handleWaitlist(session.getSessionNo());
        }
        return "取消预约成功！";
    }

    /**
     * 处理候补逻辑（被退课操作触发）
     */
    private void handleWaitlist(String sessionNo) {
        String queueKey = WAITLIST_QUEUE_PREFIX + sessionNo;
        
        while (true) {
            String nextStudentNo = stringRedisTemplate.opsForList().leftPop(queueKey);
            if (nextStudentNo == null) {
                break; 
            }

            String userKey = WAITLIST_USER_PREFIX + sessionNo + ":" + nextStudentNo;
            stringRedisTemplate.delete(userKey);

            try {
                doBookSession(nextStudentNo, sessionNo, true);
                
                // 补位成功后，更新 MySQL 里的状态为 BOOKED，方便核对
                UpdateWrapper<WaitlistRecord> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("session_no", sessionNo)
                             .eq("student_no", nextStudentNo)
                             .set("status", "BOOKED");
                waitlistRecordMapper.update(null, updateWrapper);
                
                break; 
            } catch (Exception e) {
                log.warn("候补自动预约失败: {}，原因: {}", nextStudentNo, e.getMessage());
                // 如果失败，可以考虑更新 MySQL 状态为 FAILED，或者直接跳过
            }
        }
    }
    
    @Override
    public Object listMyBookings(String studentNo, Integer page, Integer size) {
        boolean paged = page != null && size != null;

        QueryWrapper<StudentBooking> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("student_no", studentNo).eq("status", 1);

        List<StudentBooking> bookingList;
        long total = 0;
        if (paged) {
            Page<StudentBooking> bookingPage = bookingMapper.selectPage(new Page<>(page, size), queryWrapper);
            bookingList = bookingPage.getRecords();
            total = bookingPage.getTotal();
        } else {
            bookingList = QueryLimits.list(bookingMapper, queryWrapper);
        }

        if (bookingList.isEmpty()) {
            return paged ? new PageResult<>(0, new ArrayList<>()) : new ArrayList<>();
        }

        // 批量查询场次、项目、教师、报告，消灭 N+1
        List<String> sessionNos = bookingList.stream().map(StudentBooking::getSessionNo).collect(Collectors.toList());

        Map<String, ExpSession> sessionMap = sessionMapper.selectBatchIds(sessionNos).stream()
                .collect(Collectors.toMap(ExpSession::getSessionNo, s -> s, (k1, k2) -> k1));

        Set<String> projectNos = new HashSet<>();
        Set<String> workNos = new HashSet<>();
        for (ExpSession session : sessionMap.values()) {
            if (session.getProjectNo() != null) projectNos.add(session.getProjectNo());
            if (session.getWorkNo() != null) workNos.add(session.getWorkNo());
        }

        Map<String, ExpProject> projectMap = projectNos.isEmpty() ? new HashMap<>() :
                projectMapper.selectBatchIds(projectNos).stream()
                        .collect(Collectors.toMap(ExpProject::getProjectNo, p -> p, (k1, k2) -> k1));

        Map<String, String> teacherMap = workNos.isEmpty() ? new HashMap<>() :
                teacherMapper.selectBatchIds(workNos).stream()
                        .collect(Collectors.toMap(Teacher::getWorkNo, Teacher::getName, (k1, k2) -> k1));

        List<ExpReport> reports = projectNos.isEmpty() ? new ArrayList<>() :
                reportMapper.selectList(new QueryWrapper<ExpReport>()
                        .eq("student_no", studentNo)
                        .in("project_no", projectNos)
                        .eq("is_latest", 1));
        Map<String, ExpReport> reportMap = reports.stream()
                .collect(Collectors.toMap(ExpReport::getProjectNo, r -> r, (k1, k2) -> k1));

        List<SessionVO> resultList = new ArrayList<>();
        for (StudentBooking booking : bookingList) {
            ExpSession session = sessionMap.get(booking.getSessionNo());
            if (session != null) {
                SessionVO vo = new SessionVO();
                BeanUtils.copyProperties(session, vo);
                vo.setBookingNo(booking.getBookingNo());

                if (session.getWorkNo() != null) {
                    vo.setTeacherName(teacherMap.getOrDefault(session.getWorkNo(), "未知教师"));
                }

                if (session.getProjectNo() != null) {
                    ExpProject project = projectMap.get(session.getProjectNo());
                    if (project != null) {
                        vo.setProjectName(project.getProjectName());
                        vo.setGuideFile(project.getGuideFile());
                    }

                    ExpReport report = reportMap.get(session.getProjectNo());
                    if (report != null) {
                        vo.setScore(report.getScore());
                        vo.setComment(report.getComment());
                        vo.setIsGraded(report.getScore() != null);
                    } else {
                        vo.setIsGraded(false);
                    }
                }
                resultList.add(vo);
            }
        }

        return paged ? new PageResult<>(total, resultList) : resultList;
    }
}