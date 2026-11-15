package com.lab.labsystem.service.student.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.ErrorCode;
import com.lab.labsystem.entity.ExpProject;
import com.lab.labsystem.entity.ExpSession;
import com.lab.labsystem.entity.StudentBooking;
import com.lab.labsystem.entity.StudentTeachClass;
import com.lab.labsystem.entity.WaitlistRecord;
import com.lab.labsystem.mapper.ExpProjectMapper;
import com.lab.labsystem.mapper.ExpSessionMapper;
import com.lab.labsystem.mapper.StudentBookingMapper;
import com.lab.labsystem.mapper.StudentTeachClassMapper;
import com.lab.labsystem.mapper.WaitlistRecordMapper;
import com.lab.labsystem.service.student.WaitlistService;
import com.lab.labsystem.websocket.GlobalWebSocket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class WaitlistServiceImpl implements WaitlistService {

    @Autowired private ExpSessionMapper sessionMapper;
    @Autowired private ExpProjectMapper projectMapper;
    @Autowired private StudentTeachClassMapper studentTeachClassMapper;
    @Autowired private StudentBookingMapper bookingMapper;
    @Autowired private WaitlistRecordMapper waitlistRecordMapper;
    @Autowired private StringRedisTemplate stringRedisTemplate;
    
    // 这里必须有这个字段，否则测试类的反射注入会报错
    @Autowired private TransactionTemplate transactionTemplate;

    private static final String WAITLIST_QUEUE_PREFIX = "waitlist:queue:";
    private static final String WAITLIST_USER_PREFIX = "waitlist:user:";

    @Override
    public void processWaitlist(String sessionNo) {
        String queueKey = WAITLIST_QUEUE_PREFIX + sessionNo;

        Long queueSize = stringRedisTemplate.opsForList().size(queueKey);
        if (queueSize == null || queueSize == 0) {
            return;
        }

        int maxAttempts = queueSize.intValue();

        for (int i = 0; i < maxAttempts; i++) {
            String nextStudentNo = stringRedisTemplate.opsForList().leftPop(queueKey);
            if (nextStudentNo == null) {
                break;
            }

            String userKey = WAITLIST_USER_PREFIX + sessionNo + ":" + nextStudentNo;

            try {
                Boolean success = transactionTemplate.execute(status -> {
                    try {
                        doAutoBook(nextStudentNo, sessionNo);

                        stringRedisTemplate.delete(userKey);
                        UpdateWrapper<WaitlistRecord> updateWrapper = new UpdateWrapper<>();
                        updateWrapper.eq("session_no", sessionNo)
                                .eq("student_no", nextStudentNo)
                                .set("status", "BOOKED");
                        waitlistRecordMapper.update(null, updateWrapper);
                        return true;
                    } catch (Exception e) {
                        status.setRollbackOnly();
                        throw e;
                    }
                });

                if (Boolean.TRUE.equals(success)) {
                    break;
                }

            } catch (BusinessException e) {
                log.warn("候补补位失败(业务拒绝): {}，原因: {}。将其移出队列", nextStudentNo, e.getMessage());

                stringRedisTemplate.delete(userKey);
                UpdateWrapper<WaitlistRecord> failWrapper = new UpdateWrapper<>();
                failWrapper.eq("session_no", sessionNo)
                        .eq("student_no", nextStudentNo)
                        .set("status", "FAILED");
                waitlistRecordMapper.update(null, failWrapper);

            } catch (Exception e) {
                log.error("候补补位失败(系统异常): {}，原因: {}。将其移至队尾重试", nextStudentNo, e.getMessage());
                stringRedisTemplate.opsForList().rightPush(queueKey, nextStudentNo);
            }
        }
    }

    private void doAutoBook(String studentNo, String sessionNo) {
        ExpSession targetSession = sessionMapper.selectById(sessionNo);
        if (targetSession == null) {
            throw BusinessException.of(ErrorCode.DATA_NOT_FOUND, "该场次不存在");
        }

        ExpProject project = projectMapper.selectById(targetSession.getProjectNo());
        if (project != null && project.getClassNo() != null) {
            QueryWrapper<StudentTeachClass> scQw = new QueryWrapper<StudentTeachClass>()
                    .eq("student_no", studentNo)
                    .eq("class_no", project.getClassNo());
            if (studentTeachClassMapper.selectCount(scQw) == 0) {
                throw BusinessException.of(ErrorCode.BAD_REQUEST, "尚未加入该实验所属的教学班");
            }
        }

        QueryWrapper<StudentBooking> qw = new QueryWrapper<>();
        qw.eq("student_no", studentNo).eq("status", 1);
        List<StudentBooking> myBookings = bookingMapper.selectList(qw);

        for (StudentBooking b : myBookings) {
            ExpSession existingSession = sessionMapper.selectById(b.getSessionNo());
            if (existingSession != null) {
                if (existingSession.getSessionNo().equals(sessionNo)) {
                    throw BusinessException.of(ErrorCode.BAD_REQUEST, "已预约该场次");
                }
                if (existingSession.getSemester().equals(targetSession.getSemester()) &&
                        existingSession.getWeekNum().equals(targetSession.getWeekNum()) &&
                        existingSession.getDayOfWeek().equals(targetSession.getDayOfWeek()) &&
                        existingSession.getPeriod().equals(targetSession.getPeriod())) {
                    throw BusinessException.of(ErrorCode.BAD_REQUEST, "时间冲突");
                }
            }
        }

        UpdateWrapper<ExpSession> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("session_no", sessionNo)
                .gt("remaining_seats", 0)
                .setSql("remaining_seats = remaining_seats - 1");
        int rows = sessionMapper.update(null, updateWrapper);
        if (rows == 0) {
            throw BusinessException.of(ErrorCode.BAD_REQUEST, "该场次已被约满");
        }

        StudentBooking booking = new StudentBooking();
        booking.setBookingNo("B" + UUID.randomUUID().toString().substring(0, 8));
        booking.setStudentNo(studentNo);
        booking.setSessionNo(sessionNo);
        booking.setBookingTime(LocalDateTime.now());
        booking.setStatus(1);
        bookingMapper.insert(booking);

        ExpSession updatedSession = sessionMapper.selectById(sessionNo);
        if (updatedSession != null) {
            String broadcastMsg = "{\"type\":\"SEAT_UPDATE\",\"sessionNo\":\"" + sessionNo + "\",\"seats\":" + updatedSession.getRemainingSeats() + "}";
            GlobalWebSocket.broadcastToRole("all", broadcastMsg);
        }
        String notifyMsg = "{\"type\":\"WAITLIST_SUCCESS\",\"message\":\"您候补的实验场次已成功为您预约！\"}";
        GlobalWebSocket.sendToUser(studentNo, notifyMsg);
    }
}