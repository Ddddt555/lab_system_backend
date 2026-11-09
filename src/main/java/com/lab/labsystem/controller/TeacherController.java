package com.lab.labsystem.controller;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.labsystem.annotation.LogRecord;
import com.lab.labsystem.annotation.RequireRole;
import com.lab.labsystem.common.PageResult;
import com.lab.labsystem.common.QueryLimits;
import com.lab.labsystem.common.Result;
// 🛑【关键引入】引入我们即将创建的文件类型校验工具类
import com.lab.labsystem.entity.*;
import com.lab.labsystem.mapper.*;
import com.lab.labsystem.service.TeacherScoreService;
import com.lab.labsystem.service.TeacherProjectService;
import com.lab.labsystem.service.AttendanceService;
import com.lab.labsystem.vo.SessionVO;
import com.lab.labsystem.vo.StudentVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

@Tag(name = "教师端接口", description = "包含实验项目发布、场次排课、成绩管理、数据统计等功能")
@RequireRole("teacher")
@RestController
@RequestMapping("/api/teacher")
@Slf4j
public class TeacherController {

    @Autowired private ExpProjectMapper projectMapper;
    @Autowired private LabRoomMapper labRoomMapper;
    @Autowired private ExpSessionMapper sessionMapper;
    @Autowired private StudentBookingMapper bookingMapper;
    @Autowired private StudentMapper studentMapper;
    @Autowired private com.lab.labsystem.mapper.TeacherMapper teacherMapper;
    @Autowired private SysSemesterMapper semesterMapper;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private com.lab.labsystem.service.MustChangePasswordCache mustChangePasswordCache;
    @Autowired private ExpReportMapper reportMapper;
    @Autowired private ExpCourseMapper courseMapper;
    @Autowired private TeachClassMapper teachClassMapper;
    @Autowired private com.lab.labsystem.common.NumberGenerator numberGenerator;
    @Autowired private TeacherScoreService teacherScoreService;
    @Autowired private TeacherProjectService teacherProjectService;
    @Autowired private AttendanceService attendanceService;

    // ================= 0. 基础数据 =================
    @Operation(summary = "获取我的教学班列表", description = "用于下拉框选择，返回当前教师负责的所有教学班")
    @GetMapping("/my-classes")
    public Result<List<Map<String, String>>> getMyClasses(HttpServletRequest request) {
        String workNo = (String) request.getAttribute("currentUserNo");
        List<TeachClass> classes = teachClassMapper.selectList(
                new QueryWrapper<TeachClass>().eq("work_no", workNo));

        if (classes.isEmpty()) return Result.success(new ArrayList<>());

        // 🛑【核心优化】批量查询课程，消灭 N+1
        List<String> courseNos = classes.stream().map(TeachClass::getCourseNo).distinct().collect(Collectors.toList());
        Map<String, String> courseMap = courseNos.isEmpty() ? new HashMap<>() :
                courseMapper.selectBatchIds(courseNos).stream()
                        .collect(Collectors.toMap(ExpCourse::getCourseNo, ExpCourse::getCourseName, (k1, k2) -> k1));

        List<Map<String, String>> resultList = new ArrayList<>();
        for (TeachClass tc : classes) {
            Map<String, String> map = new HashMap<>();
            map.put("classNo", tc.getClassNo());
            String courseName = courseMap.getOrDefault(tc.getCourseNo(), "未知课程");
            map.put("displayName", tc.getClassNo() + " - " + courseName + " (" + tc.getSemester() + ")");
            map.put("dailyWeight", String.valueOf(tc.getDailyWeight() != null ? tc.getDailyWeight() : 30));
            map.put("reportWeight", String.valueOf(tc.getReportWeight() != null ? tc.getReportWeight() : 70));
            resultList.add(map);
        }
        return Result.success(resultList);
    }

    @Operation(summary = "获取实验室列表")
    @GetMapping("/labs")
    public Result<List<LabRoom>> getAllLabs() {
        return Result.success(labRoomMapper.selectList(null));
    }

    @Operation(summary = "获取学期列表")
    @GetMapping("/semesters")
    public Result<List<SysSemester>> getAllSemesters() {
        return Result.success(semesterMapper.selectList(null));
    }

    // ================= 1. 实验项目管理 =================
    @Operation(summary = "获取我的实验项目列表", description = "只返回当前教师负责的教学班下的项目")
    @GetMapping("/project/list")
    public Result<Object> getProjectList(
            HttpServletRequest request,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        String workNo = (String) request.getAttribute("currentUserNo");
        return Result.success(teacherProjectService.listMyProjects(workNo, page, size));
    }

    @Operation(summary = "发布实验项目")
    @LogRecord("发布实验项目")
    @PostMapping("/project/add")
    public Result<String> addProject(@RequestBody ExpProject project, HttpServletRequest request) {
        String currentWorkNo = (String) request.getAttribute("currentUserNo");
        return Result.success(teacherProjectService.addProject(project, currentWorkNo));
    }

    @Operation(summary = "上传实验指导书")
    @LogRecord("上传指导书")
    @PostMapping("/project/upload-guide")
    public Result<String> uploadGuide(@RequestParam("file") MultipartFile file,
                                      @RequestParam("projectNo") String projectNo,
                                      HttpServletRequest request) {
        String currentWorkNo = (String) request.getAttribute("currentUserNo");
        return Result.success(teacherProjectService.uploadGuide(file, projectNo, currentWorkNo));
    }

    // ================= 2. 实验场次安排 =================
    @Operation(summary = "安排实验场次", description = "包含教师时间冲突、实验室占用、教学班冲突等多重校验")
    @LogRecord("安排实验场次")
    @PostMapping("/session/add")
    public Result<String> addSession(@RequestBody ExpSession session, HttpServletRequest request) {
        String currentWorkNo = (String) request.getAttribute("currentUserNo");
        session.setWorkNo(currentWorkNo);

        ExpProject project = projectMapper.selectById(session.getProjectNo());
        if (project == null) return Result.error("实验项目不存在！");
        TeachClass tc = teachClassMapper.selectById(project.getClassNo());
        if (tc == null || !tc.getWorkNo().equals(currentWorkNo)) {
            return Result.error("非法操作：您无权安排其他老师的实验项目！");
        }

        LabRoom room = labRoomMapper.selectById(session.getRoomNo());
        if (room == null) return Result.error("实验室不存在！");
        if (session.getCapacity() > room.getCapacity()) return Result.error("排课容量不能超过实验室容量！");

        QueryWrapper<ExpSession> conflictQw = new QueryWrapper<ExpSession>()
                .eq("semester", session.getSemester())
                .eq("week_num", session.getWeekNum())
                .eq("day_of_week", session.getDayOfWeek())
                .eq("period", session.getPeriod());

        List<ExpSession> existSessions = sessionMapper.selectList(conflictQw);
        for (ExpSession exist : existSessions) {
            if (exist.getWorkNo().equals(currentWorkNo)) {
                return Result.error("排课失败：您在【第" + session.getWeekNum() + "周 "
                        + getWeekDayName(session.getDayOfWeek()) + " " + session.getPeriod() + "】已有其他排课！");
            }
            if (exist.getRoomNo().equals(session.getRoomNo())) {
                return Result.error("排课失败：实验室【" + room.getRoomName() + "】在【第"
                        + session.getWeekNum() + "周 " + getWeekDayName(session.getDayOfWeek())
                        + " " + session.getPeriod() + "】已被占用！");
            }
            ExpProject existProject = projectMapper.selectById(exist.getProjectNo());
            if (existProject != null && existProject.getClassNo() != null
                    && existProject.getClassNo().equals(project.getClassNo())) {
                return Result.error("排课失败：该教学班在此时段已经有排课了！");
            }
        }

        session.setSessionNo(numberGenerator.generateSessionNo());
        LocalDateTime absoluteTime = calculateAbsoluteTime(
                session.getSemester(), session.getWeekNum(),
                session.getDayOfWeek(), session.getPeriod());
        session.setSessionTime(absoluteTime);
        session.setRemainingSeats(session.getCapacity());

        sessionMapper.insert(session);
        return Result.success("场次安排成功！场次号为：" + session.getSessionNo());
    }

    private String getWeekDayName(Integer dayOfWeek) {
        String[] days = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        if (dayOfWeek != null && dayOfWeek >= 1 && dayOfWeek <= 7) return days[dayOfWeek];
        return "未知";
    }

    private LocalDateTime calculateAbsoluteTime(String semesterName, Integer weekNum,
                                                Integer dayOfWeek, String period) {
        SysSemester semester = semesterMapper.selectById(semesterName);
        if (semester == null) throw new RuntimeException("排课失败：该学期配置不存在！");
        LocalDate targetDate = semester.getStartDate()
                .plusWeeks(weekNum - 1).plusDays(dayOfWeek - 1);
        LocalTime targetTime;
        switch (period) {
            case "1-2节": targetTime = LocalTime.of(8, 0); break;
            case "3-4节": targetTime = LocalTime.of(10, 0); break;
            case "5-6节": targetTime = LocalTime.of(14, 0); break;
            case "7-8节": targetTime = LocalTime.of(16, 0); break;
            case "9-10节": targetTime = LocalTime.of(19, 0); break;
            default: targetTime = LocalTime.of(8, 0);
        }
        return LocalDateTime.of(targetDate, targetTime);
    }

    @Operation(summary = "获取我负责的实验场次列表")
    @GetMapping("/my-sessions")
    public Result<Object> getMySessions(
            HttpServletRequest request,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        String workNo = (String) request.getAttribute("currentUserNo");
        QueryWrapper<ExpSession> qw = new QueryWrapper<ExpSession>().eq("work_no", workNo).orderByAsc("session_time");

        List<ExpSession> sessionList;
        long total = 0;
        if (page != null && size != null) {
            Page<ExpSession> sessionPage = sessionMapper.selectPage(new Page<>(page, size), qw);
            sessionList = sessionPage.getRecords();
            total = sessionPage.getTotal();
        } else {
            sessionList = QueryLimits.list(sessionMapper, qw);
        }

        if (sessionList.isEmpty()) {
            if (page != null && size != null) return Result.success(new PageResult<>(0, new ArrayList<>()));
            return Result.success(new ArrayList<>());
        }

        // 🛑【核心优化】批量查询项目，消灭 N+1
        List<String> projectNos = sessionList.stream().map(ExpSession::getProjectNo).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<String, ExpProject> projectMap = projectNos.isEmpty() ? new HashMap<>() :
                projectMapper.selectBatchIds(projectNos).stream()
                        .collect(Collectors.toMap(ExpProject::getProjectNo, p -> p, (k1, k2) -> k1));

        List<SessionVO> resultList = new ArrayList<>();
        for (ExpSession session : sessionList) {
            SessionVO vo = new SessionVO();
            BeanUtils.copyProperties(session, vo);
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

    // ================= 3. 查看名单 =================
    @Operation(summary = "查看场次预约学生名单")
    @GetMapping("/session-bookings")
    public Result<List<StudentVO>> getSessionBookings(@RequestParam String sessionNo,
                                                      HttpServletRequest request) {
        String currentWorkNo = (String) request.getAttribute("currentUserNo");
        ExpSession session = sessionMapper.selectById(sessionNo);
        if (session == null || !session.getWorkNo().equals(currentWorkNo))
            return Result.error("非法操作：您无权查看！");

        List<StudentBooking> bookings = bookingMapper.selectList(
                new QueryWrapper<StudentBooking>().eq("session_no", sessionNo).eq("status", 1));
        
        if (bookings.isEmpty()) return Result.success(new ArrayList<>());

        // 🛑【核心优化】批量查询学生信息，消灭 N+1
        List<String> studentNos = bookings.stream().map(StudentBooking::getStudentNo).distinct().collect(Collectors.toList());
        Map<String, Student> studentMap = studentMapper.selectBatchIds(studentNos).stream()
                .collect(Collectors.toMap(Student::getStudentNo, s -> s, (k1, k2) -> k1));

        List<StudentVO> resultList = new ArrayList<>();
        for (StudentBooking booking : bookings) {
            Student student = studentMap.get(booking.getStudentNo());
            if (student != null) {
                StudentVO vo = new StudentVO();
                BeanUtils.copyProperties(student, vo);
                vo.setBookingTime(booking.getBookingTime());
                resultList.add(vo);
            }
        }
        return Result.success(resultList);
    }

    @Operation(summary = "取消排课")
    @LogRecord("取消排课")
    @DeleteMapping("/session/delete")
    public Result<String> deleteSession(@RequestParam String sessionNo, HttpServletRequest request) {
        String currentWorkNo = (String) request.getAttribute("currentUserNo");
        ExpSession session = sessionMapper.selectById(sessionNo);
        if (session == null || !session.getWorkNo().equals(currentWorkNo)) return Result.error("非法操作！");
        
        sessionMapper.deleteById(sessionNo);
        return Result.success("取消排课成功！");
    }

    // ================= 4. 成绩管理 =================
    @Operation(summary = "获取教学班成绩列表")
    @GetMapping("/scores")
    public Result<Object> getCourseScores(
            @RequestParam String classNo,
            HttpServletRequest request,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        String currentWorkNo = (String) request.getAttribute("currentUserNo");
        return Result.success(teacherScoreService.getCourseScores(currentWorkNo, classNo, page, size));
    }

    @Operation(summary = "录入平时成绩并自动计算总评")
    @LogRecord("录入平时成绩")
    @PostMapping("/score/save")
    public Result<String> saveScore(@RequestParam String studentNo,
                                    @RequestParam String classNo,
                                    @RequestParam Double dailyScore,
                                    HttpServletRequest request) {
        String currentWorkNo = (String) request.getAttribute("currentUserNo");
        return Result.success(teacherScoreService.saveScore(currentWorkNo, studentNo, classNo, dailyScore));
    }

    @Operation(summary = "修改成绩权重并重算")
    @LogRecord("修改成绩权重")
    @PostMapping("/score/weight")
    public Result<String> updateWeight(
            @RequestParam String classNo,
            @RequestParam Integer dailyWeight,
            @RequestParam Integer reportWeight,
            HttpServletRequest request) {
        String workNo = (String) request.getAttribute("currentUserNo");
        return Result.success(teacherScoreService.updateWeight(workNo, classNo, dailyWeight, reportWeight));
    }

    // ================= 5. 修改密码 =================
    @Operation(summary = "教师修改密码")
    @LogRecord("修改密码")
    @PostMapping("/update-password")
    public Result<String> updatePassword(HttpServletRequest request,
                                         @RequestParam String oldPwd,
                                         @RequestParam String newPwd) {
    	 String errorMsg = com.lab.labsystem.common.PasswordValidator.validate(newPwd);
         if (errorMsg != null) return Result.error(errorMsg);
        String workNo = (String) request.getAttribute("currentUserNo");
        Teacher teacher = teacherMapper.selectById(workNo);
        if (teacher == null || !passwordEncoder.matches(oldPwd, teacher.getPassword()))
            return Result.error("原密码错误！");
        teacher.setPassword(passwordEncoder.encode(newPwd));
        
        // 🛑【核心修复】改密成功后，重置强制改密状态和更新时间
        teacher.setMustChangePassword(0);
        teacher.setPwdUpdateTime(java.time.LocalDateTime.now());
        
        teacherMapper.updateById(teacher);
        // 【缓存一致性】改密成功后清除强制改密缓存
        mustChangePasswordCache.evict("teacher", workNo);
        return Result.success("密码修改成功！");
    }

    // ================= 6. 数据统计 =================
    @Operation(summary = "获取成绩分布统计")
    @GetMapping("/stats/score-distribution")
    public Result<Map<String, Integer>> getScoreDistribution(HttpServletRequest request) {
        String currentWorkNo = (String) request.getAttribute("currentUserNo");
        return Result.success(teacherScoreService.getScoreDistribution(currentWorkNo));
    }

    @Operation(summary = "获取实验开出率统计")
    @GetMapping("/stats/experiment-rate")
    public Result<Map<String, Object>> getExperimentRate(HttpServletRequest request) {
        String currentWorkNo = (String) request.getAttribute("currentUserNo");
        return Result.success(teacherScoreService.getExperimentRate(currentWorkNo));
    }

    // ================= 7. 导出成绩 =================
    @Operation(summary = "导出教学班成绩单 Excel")
    @GetMapping("/score/export")
    public void exportScores(@RequestParam String classNo, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String currentWorkNo = (String) request.getAttribute("currentUserNo");
        teacherScoreService.exportScores(currentWorkNo, classNo, response);
    }

    // ================= 8. 考勤管理 =================
    @Operation(summary = "教师发起签到")
    @PostMapping("/attendance/start")
    public Result<String> startAttendance(@RequestParam String sessionNo, HttpServletRequest request) {
        String workNo = (String) request.getAttribute("currentUserNo");
        return Result.success(attendanceService.startAttendance(workNo, sessionNo));
    }

    @Operation(summary = "获取已签到学生名单")
    @GetMapping("/attendance/list")
    public Result<List<StudentVO>> getAttendanceList(@RequestParam String sessionNo, HttpServletRequest request) {
        String workNo = (String) request.getAttribute("currentUserNo");
        return Result.success(attendanceService.getAttendanceList(workNo, sessionNo));
    }
    
    @Operation(summary = "导出场次考勤Excel")
    @GetMapping("/attendance/export")
    public void exportAttendance(@RequestParam String sessionNo, HttpServletRequest request, HttpServletResponse response) throws Exception {
        String workNo = (String) request.getAttribute("currentUserNo");
        attendanceService.exportAttendance(workNo, sessionNo, response);
    }
}