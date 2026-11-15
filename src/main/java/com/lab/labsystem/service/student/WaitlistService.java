package com.lab.labsystem.service.student;

public interface WaitlistService {
    /**
     * 处理候补队列的自动补位逻辑
     * @param sessionNo 场次号
     */
    void processWaitlist(String sessionNo);
}