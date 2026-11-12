package com.lab.labsystem.controller;

import com.lab.labsystem.annotation.RateLimit;
import com.lab.labsystem.annotation.RequireRole;
import com.lab.labsystem.common.Result;
import com.lab.labsystem.service.attendance.AttendanceService;
import com.lab.labsystem.service.student.CourseSelectionService;
import com.lab.labsystem.service.student.SessionQueryService;
import com.lab.labsystem.service.student.StudentBookingService;
import com.lab.labsystem.service.student.StudentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学生端接口。
 *
 * <p>本类只做身份透传与响应包装，业务分别落在：
 * {@link CourseSelectionService}（选课/退课）、{@link SessionQueryService}（场次浏览）、
 * {@link StudentBookingService}（预约/取消/候补/我的预约）、
 * {@link AttendanceService}（签到）、{@link StudentService}（改密）。
 */
@Tag(name = "学生端接口", description = "选课、预约实验场次、签到、查看报告成绩、修改密码")
@RestController
@RequireRole("student")
@RequestMapping("/api/student")
public class StudentController {

    @Autowired private CourseSelectionService courseSelectionService;
    @Autowired private SessionQueryService sessionQueryService;
    @Autowired private StudentBookingService studentBookingService;
    @Autowired private AttendanceService attendanceService;
    @Autowired private StudentService studentService;

    // ================= 0. 选课中心 =================

    @Operation(summary = "获取可选教学班列表", description = "返回所有教学班信息，并标记当前学生是否已经选过该班")
    @GetMapping("/available-classes")
    public Result<Object> getAvailableClasses(
            HttpServletRequest request,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        String studentNo = (String) request.getAttribute("currentUserNo");
        return Result.success(courseSelectionService.listAvailableClasses(studentNo, page, size));
    }

    @Operation(summary = "学生选课", description = "学生加入指定的教学班，已加入每秒限流1次")
    @RateLimit(qps = 1.0)
    @PostMapping("/select-class")
    public Result<String> selectClass(HttpServletRequest request, @RequestParam String classNo) {
        String studentNo = (String) request.getAttribute("currentUserNo");
        return Result.success(courseSelectionService.selectClass(studentNo, classNo));
    }

    @Operation(summary = "学生退课", description = "学生退出教学班。若已存在该班级的有效场次预约，则拒绝退课")
    @PostMapping("/drop-class")
    public Result<String> dropClass(HttpServletRequest request, @RequestParam String classNo) {
        String studentNo = (String) request.getAttribute("currentUserNo");
        return Result.success(courseSelectionService.dropClass(studentNo, classNo));
    }

    // ================= 1. 预约记录查询 =================

    @Operation(summary = "获取我的预约记录", description = "查询当前学生所有的实验预约状态、成绩和指导书信息")
    @GetMapping("/my-bookings")
    public Result<Object> getMyBookings(
            HttpServletRequest request,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        String studentNo = (String) request.getAttribute("currentUserNo");
        return Result.success(studentBookingService.listMyBookings(studentNo, page, size));
    }

    // ================= 2. 学生预约场次 =================

    @Operation(summary = "预约实验场次", description = "校验是否已选课、时间冲突、剩余机位后，扣减座位并生成预约记录，每秒限流2次")
    @RateLimit(qps = 2.0)
    @PostMapping("/book")
    public Result<String> bookSession(HttpServletRequest request, @RequestParam String sessionNo) {
        String studentNo = (String) request.getAttribute("currentUserNo");
        return Result.success(studentBookingService.bookSession(studentNo, sessionNo));
    }

    // ================= 3. 获取所有实验场次 =================

    @Operation(summary = "获取实验场次列表", description = "用于学生端查看当前所有开放的实验场次")
    @GetMapping("/session/list")
    public Result<Object> getSessionList(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return Result.success(sessionQueryService.listSessions(page, size));
    }

    // ================= 4. 取消预约 =================

    @Operation(summary = "取消预约", description = "学生取消某个实验场次的预约，若报告已被批阅则拦截")
    @PostMapping("/cancel")
    public Result<String> cancelBooking(HttpServletRequest request, @RequestParam String bookingNo) {
        String currentStudentNo = (String) request.getAttribute("currentUserNo");
        return Result.success(studentBookingService.cancelBooking(currentStudentNo, bookingNo));
    }

    // ================= 5. 修改密码 =================

    @Operation(summary = "学生修改密码", description = "校验原密码后更新为新密码，修改成功后建议重新登录")
    @PostMapping("/update-password")
    public Result<String> updatePassword(HttpServletRequest request,
                                         @RequestParam String oldPwd,
                                         @RequestParam String newPwd) {
        String studentNo = (String) request.getAttribute("currentUserNo");
        return Result.success(studentService.changePassword(studentNo, oldPwd, newPwd));
    }

    // ================= 6. 考勤签到 =================

    @Operation(summary = "学生签到")
    @PostMapping("/attendance/sign")
    public Result<String> signIn(
            @RequestParam String sessionNo,
            @RequestParam String code,
            HttpServletRequest request) {
        String studentNo = (String) request.getAttribute("currentUserNo");
        return Result.success(attendanceService.signIn(studentNo, sessionNo, code));
    }

    @Operation(summary = "加入候补队列")
    @PostMapping("/waitlist")
    public Result<String> joinWaitlist(HttpServletRequest request, @RequestParam String sessionNo) {
        String studentNo = (String) request.getAttribute("currentUserNo");
        return Result.success(studentBookingService.joinWaitlist(studentNo, sessionNo));
    }
}
