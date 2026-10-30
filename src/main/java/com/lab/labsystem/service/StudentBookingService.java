package com.lab.labsystem.service;

import jakarta.servlet.http.HttpServletRequest;

public interface StudentBookingService {
    String bookSession(String studentNo, String sessionNo);
    String cancelBooking(String studentNo, String bookingNo);
    // 加入候补队列
    String joinWaitlist(String studentNo, String sessionNo);
}