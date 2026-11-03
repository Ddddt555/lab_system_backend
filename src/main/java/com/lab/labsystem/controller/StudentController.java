package com.lab.labsystem.controller;
import com.lab.labsystem.annotation.RequireRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Collectors;

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
import com.lab.labsystem.service.AttendanceService;
import com.lab.labsystem.vo.SessionVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "学生端接口", description = "包含选课中心、场次预约、报告提交、密码修改等功能")
@RestController
@RequireRole("student")
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

        if (allClasses.isEmpty()) {
            if (page != null && size != null) return Result.success(new PageResult<>(0, new ArrayList<>()));
            return Result.success(new ArrayList<>());
        }

        // 🛑【核心优化】批量查询，消灭 N+1
        List<String> classNos = allClasses.stream().map(TeachClass::getClassNo).collect(Collectors.toList());
        List<String> courseNos = allClasses.stream().map(TeachClass::getCourseNo).distinct().collect(Collectors.toList());
        List<String> workNos = allClasses.stream().map(TeachClass::getWorkNo).distinct().collect(Collectors.toList());

        // 1. 批量查课程
        Map<String, String> courseMap = courseNos.isEmpty() ? new HashMap<>() : 
                courseMapper.selectBatchIds(courseNos).stream()
                .collect(Collectors.toMap(ExpCourse::getCourseNo, ExpCourse::getCourseName, (k1, k2) -> k1));

        // 2. 批量查教师
        Map<String, String> teacherMap = workNos.isEmpty() ? new HashMap<>() : 
                teacherMapper.selectBatchIds(workNos).stream()
                .collect(Collectors.toMap(Teacher::getWorkNo, Teacher::getName, (k1, k2) -> k1));

        // 3. 批量查该学生选了哪些班
        List<StudentTeachClass> selectedList = studentTeachClassMapper.selectList(
                new QueryWrapper<StudentTeachClass>().eq("student_no", studentNo).in("class_no", classNos));
        Set<String> selectedClassNos = selectedList.stream().map(StudentTeachClass::getClassNo).collect(Collectors.toSet());

        List<Map<String, Object>> resultList = new ArrayList<>();
        for (TeachClass tc : allClasses) {
            Map<String, Object> map = new HashMap<>();
            map.put("classNo", tc.getClassNo());
            map.put("semester", tc.getSemester());
            map.put("courseName", courseMap.getOrDefault(tc.getCourseNo(), "未知课程"));
            map.put("teacherName", teacherMap.getOrDefault(tc.getWorkNo(), "未知教师"));
            map.put("isSelected", selectedClassNos.contains(tc.getClassNo()));
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

        if (bookingList.isEmpty()) {
            if (page != null && size != null) return Result.success(new PageResult<>(0, new ArrayList<>()));
            return Result.success(new ArrayList<>());
        }

        // 🛑【核心优化】批量查询场次、项目、教师、报告
        List<String> sessionNos = bookingList.stream().map(StudentBooking::getSessionNo).collect(Collectors.toList());
        
        // 1. 批量查场次
        Map<String, ExpSession> sessionMap = sessionMapper.selectBatchIds(sessionNos).stream()
                .collect(Collectors.toMap(ExpSession::getSessionNo, s -> s, (k1, k2) -> k1));

        // 2. 收集项目号和教师工号
        Set<String> projectNos = new HashSet<>();
        Set<String> workNos = new HashSet<>();
        for (ExpSession session : sessionMap.values()) {
            if (session.getProjectNo() != null) projectNos.add(session.getProjectNo());
            if (session.getWorkNo() != null) workNos.add(session.getWorkNo());
        }

        // 3. 批量查项目
        Map<String, ExpProject> projectMap = projectNos.isEmpty() ? new HashMap<>() : 
                projectMapper.selectBatchIds(projectNos).stream()
                .collect(Collectors.toMap(ExpProject::getProjectNo, p -> p, (k1, k2) -> k1));

        // 4. 批量查教师
        Map<String, String> teacherMap = workNos.isEmpty() ? new HashMap<>() : 
                teacherMapper.selectBatchIds(workNos).stream()
                .collect(Collectors.toMap(Teacher::getWorkNo, Teacher::getName, (k1, k2) -> k1));

        // 5. 批量查最新报告
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

        if (sessionList.isEmpty()) {
            if (page != null && size != null) return Result.success(new PageResult<>(0, new ArrayList<>()));
            return Result.success(new ArrayList<>());
        }

        // 🛑【核心优化】批量查询教师和项目，消灭 N+1
        Set<String> workNos = sessionList.stream().map(ExpSession::getWorkNo).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<String> projectNos = sessionList.stream().map(ExpSession::getProjectNo).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<String, String> teacherMap = workNos.isEmpty() ? new HashMap<>() : 
                teacherMapper.selectBatchIds(workNos).stream()
                .collect(Collectors.toMap(Teacher::getWorkNo, Teacher::getName, (k1, k2) -> k1));

        Map<String, ExpProject> projectMap = projectNos.isEmpty() ? new HashMap<>() : 
                projectMapper.selectBatchIds(projectNos).stream()
                .collect(Collectors.toMap(ExpProject::getProjectNo, p -> p, (k1, k2) -> k1));

        List<SessionVO> resultList = new ArrayList<>();
        for (ExpSession session : sessionList) {
            SessionVO vo = new SessionVO();
            BeanUtils.copyProperties(session, vo);

            if (session.getWorkNo() != null) {
                vo.setTeacherName(teacherMap.getOrDefault(session.getWorkNo(), "未知教师"));
            }

            if (session.getProjectNo() != null) {
                ExpProject project = projectMap.get(session.getProjectNo());
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
    	String errorMsg = com.lab.labsystem.common.PasswordValidator.validate(newPwd);
        if (errorMsg != null) return Result.error(errorMsg);
        String studentNo = (String) request.getAttribute("currentUserNo");
        com.lab.labsystem.entity.Student student = studentMapper.selectById(studentNo);
        if (student == null || !passwordEncoder.matches(oldPwd, student.getPassword())) {
            return Result.error("原密码错误！");
        }
        student.setPassword(passwordEncoder.encode(newPwd));
        
        // 🛑【核心修复】改密成功后，重置强制改密状态和更新时间
        student.setMustChangePassword(0);
        student.setPwdUpdateTime(java.time.LocalDateTime.now());
        
        studentMapper.updateById(student);
        return Result.success("密码修改成功，请重新登录！");
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