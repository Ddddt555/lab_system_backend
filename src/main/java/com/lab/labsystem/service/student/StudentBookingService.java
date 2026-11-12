package com.lab.labsystem.service.student;

import jakarta.servlet.http.HttpServletRequest;

public interface StudentBookingService {
    String bookSession(String studentNo, String sessionNo);
    String cancelBooking(String studentNo, String bookingNo);
    // 加入候补队列
    String joinWaitlist(String studentNo, String sessionNo);
    // 我的预约记录（含场次、项目、教师、报告成绩的批量填充）
    Object listMyBookings(String studentNo, Integer page, Integer size);
}