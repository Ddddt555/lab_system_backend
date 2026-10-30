package com.lab.labsystem.controller;
import com.lab.labsystem.annotation.RequireRole;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.labsystem.annotation.RateLimit;
import com.lab.labsystem.common.PageResult;
import com.lab.labsystem.common.Result;
import com.lab.labsystem.entity.*;
import com.lab.labsystem.mapper.*;
import com.lab.labsystem.service.StudentBookingService;
import com.lab.labsystem.service.AttendanceService; // ⚠️ 新增
import com.lab.labsystem.vo.SessionVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "学生端接口", description = "包含选课中心、场次预约、报告提交、密码修改等功能")
@RestController
@RequireRole("student") // ⚠️ 新增：该 Controller 下所有接口，必须是学生
@RequestMapping("/api/student")
public class StudentController {

    @Autowired private ExpSessionMapper sessionMapper;
    @Autowired private ExpReportMapper reportMapper;
    @Autowired private StudentBookingMapper bookingMapper;
    @Autowired private ExpProjectMapper projectMapper;
    @Autowired private TeacherMapper teacherMapper;
    @Autowired private com.lab.labsystem.mapper.StudentMapper studentMapper;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    
    @Autowired private StudentTeachClassMapper studentTeachClassMapper;
    @Autowired private TeachClassMapper teachClassMapper;
    @Autowired private ExpCourseMapper courseMapper;

    @Autowired private StudentBookingService studentBookingService;
    
    // ⚠️ 注入考勤 Service
    @Autowired private AttendanceService attendanceService;

    // ================= 0. 选课中心 =================
    
    @Operation(summary = "获取可选教学班列表", description = "返回所有教学班信息，并标记当前学生是否已经选过该班")
    @GetMapping("/available-classes")
    public Result<Object> getAvailableClasses(
            HttpServletRequest request,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        String studentNo = (String) request.getAttribute("currentUserNo");
        
        List<TeachClass> allClasses;
        long total = 0;
        if (page != null && size != null) {
            Page<TeachClass> tcPage = teachClassMapper.selectPage(new Page<>(page, size), null);
            allClasses = tcPage.getRecords();
            total = tcPage.getTotal();
        } else {
            allClasses = teachClassMapper.selectList(null);
        }

        List<java.util.Map<String, Object>> resultList = new ArrayList<>();
        for (TeachClass tc : allClasses) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("classNo", tc.getClassNo());
            map.put("semester", tc.getSemester());

            ExpCourse course = courseMapper.selectById(tc.getCourseNo());
            map.put("courseName", course != null ? course.getCourseName() : "未知课程");

            Teacher teacher = teacherMapper.selectById(tc.getWorkNo());
            map.put("teacherName", teacher != null ? teacher.getName() : "未知教师");

            QueryWrapper<StudentTeachClass> qw = new QueryWrapper<StudentTeachClass>()
                    .eq("student_no", studentNo)
                    .eq("class_no", tc.getClassNo());
            map.put("isSelected", studentTeachClassMapper.selectCount(qw) > 0);

            resultList.add(map);
        }
        if (page != null && size != null) return Result.success(new PageResult<>(total, resultList));
        return Result.success(resultList);
    }

    @Operation(summary = "学生选课", description = "学生加入指定的教学班，已加入每秒限流1次")
    @RateLimit(qps = 1.0)
    @PostMapping("/select-class")
    public Result<String> selectClass(HttpServletRequest request, @RequestParam String classNo) {
        String studentNo = (String) request.getAttribute("currentUserNo");

        QueryWrapper<StudentTeachClass> qw = new QueryWrapper<StudentTeachClass>()
                .eq("student_no", studentNo)
                .eq("class_no", classNo);
        if (studentTeachClassMapper.selectCount(qw) > 0) {
            return Result.error("你已经加入该教学班了，请勿重复操作！");
        }

        StudentTeachClass stc = new StudentTeachClass();
        stc.setStudentNo(studentNo);
        stc.setClassNo(classNo);
        
        TeachClass tc = teachClassMapper.selectById(classNo);
        if(tc != null) stc.setSemester(tc.getSemester());
        
        studentTeachClassMapper.insert(stc);
        return Result.success("选课成功！");
    }

    @Operation(summary = "学生退课", description = "学生退出教学班。若已存在该班级的有效场次预约，则拒绝退课")
    @PostMapping("/drop-class")
    public Result<String> dropClass(HttpServletRequest request, @RequestParam String classNo) {
        String studentNo = (String) request.getAttribute("currentUserNo");

        List<ExpProject> projects = projectMapper.selectList(new QueryWrapper<ExpProject>().eq("class_no", classNo));
        if (!projects.isEmpty()) {
            List<String> projectNos = projects.stream().map(ExpProject::getProjectNo).toList();
            List<ExpSession> sessions = sessionMapper.selectList(new QueryWrapper<ExpSession>().in("project_no", projectNos));
            if (!sessions.isEmpty()) {
                List<String> sessionNos = sessions.stream().map(ExpSession::getSessionNo).toList();
                QueryWrapper<StudentBooking> bookQw = new QueryWrapper<StudentBooking>()
                        .eq("student_no", studentNo).eq("status", 1).in("session_no", sessionNos);
                if (bookingMapper.selectCount(bookQw) > 0) {
                    return Result.error("你已预约了该班级下的实验场次，请先取消预约再退课！");
                }
            }
        }

        QueryWrapper<StudentTeachClass> qw = new QueryWrapper<StudentTeachClass>()
                .eq("student_no", studentNo)
                .eq("class_no", classNo);
        studentTeachClassMapper.delete(qw);
        return Result.success("退课成功！");
    }

    // ================= 1. 原有预约记录查询 =================
    @Operation(summary = "获取我的预约记录", description = "查询当前学生所有的实验预约状态、成绩和指导书信息")
    @GetMapping("/my-bookings")
    public Result<Object> getMyBookings(
            HttpServletRequest request,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        String studentNo = (String) request.getAttribute("currentUserNo"); 
        
        QueryWrapper<StudentBooking> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("student_no", studentNo).eq("status", 1);
        
        List<StudentBooking> bookingList;
        long total = 0;
        if (page != null && size != null) {
            Page<StudentBooking> bookingPage = bookingMapper.selectPage(new Page<>(page, size), queryWrapper);
            bookingList = bookingPage.getRecords();
            total = bookingPage.getTotal();
        } else {
            bookingList = bookingMapper.selectList(queryWrapper);
        }

        List<SessionVO> resultList = new ArrayList<>();
        for (StudentBooking booking : bookingList) {
            ExpSession session = sessionMapper.selectById(booking.getSessionNo());
            if (session != null) {
                SessionVO vo = new SessionVO();
                BeanUtils.copyProperties(session, vo);
                vo.setBookingNo(booking.getBookingNo());

                if (session.getWorkNo() != null) {
                    Teacher teacher = teacherMapper.selectById(session.getWorkNo());
                    if (teacher != null) vo.setTeacherName(teacher.getName());
                }
                
                if (session.getProjectNo() != null) {
                    ExpProject project = projectMapper.selectById(session.getProjectNo());
                    if (project != null) {
                        vo.setProjectName(project.getProjectName());
                        vo.setGuideFile(project.getGuideFile());
                    }
                }

                QueryWrapper<ExpReport> reportQw = new QueryWrapper<>();
                reportQw.eq("student_no", studentNo)
                        .eq("project_no", session.getProjectNo())
                        .eq("is_latest", 1);
                ExpReport report = reportMapper.selectOne(reportQw);
                
                if (report != null) {
                    vo.setScore(report.getScore()); 
                    vo.setComment(report.getComment()); 
                }
                
                QueryWrapper<ExpReport> gradedQw = new QueryWrapper<>();
                gradedQw.eq("student_no", studentNo)
                        .eq("project_no", session.getProjectNo())
                        .isNotNull("score"); 
                vo.setIsGraded(reportMapper.selectCount(gradedQw) > 0);
                
                resultList.add(vo);
            }
        }
        if (page != null && size != null) return Result.success(new PageResult<>(total, resultList));
        return Result.success(resultList);
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
        
        List<ExpSession> sessionList;
        long total = 0;
        if (page != null && size != null) {
            Page<ExpSession> sessionPage = sessionMapper.selectPage(new Page<>(page, size), null);
            sessionList = sessionPage.getRecords();
            total = sessionPage.getTotal();
        } else {
            sessionList = sessionMapper.selectList(null);
        }

        List<SessionVO> resultList = new ArrayList<>();
        for (ExpSession session : sessionList) {
            SessionVO vo = new SessionVO();
            BeanUtils.copyProperties(session, vo);

            if (session.getWorkNo() != null) {
                Teacher teacher = teacherMapper.selectById(session.getWorkNo());
                if (teacher != null) {
                    vo.setTeacherName(teacher.getName());
                }
            }

            if (session.getProjectNo() != null) {
                ExpProject project = projectMapper.selectById(session.getProjectNo());
                if (project != null) {
                    vo.setProjectName(project.getProjectName());
                    vo.setGuideFile(project.getGuideFile()); 
                }
            }
            resultList.add(vo);
        }
        if (page != null && size != null) return Result.success(new PageResult<>(total, resultList));
        return Result.success(resultList);
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
    public Result<String> updatePassword(HttpServletRequest request, @RequestParam String oldPwd, @RequestParam String newPwd) {
        String studentNo = (String) request.getAttribute("currentUserNo");
        com.lab.labsystem.entity.Student student = studentMapper.selectById(studentNo);
        if (student == null || !passwordEncoder.matches(oldPwd, student.getPassword())) {
            return Result.error("原密码错误！");
        }
        student.setPassword(passwordEncoder.encode(newPwd));
        studentMapper.updateById(student);
        return Result.success("密码修改成功，请重新登录！");
    }

    // ================= 6. 考勤签到 (新增) =================
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