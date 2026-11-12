package com.lab.labsystem.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.lab.labsystem.dto.request.ProjectCreateDTO;
import com.lab.labsystem.dto.request.SessionCreateDTO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.lab.labsystem.annotation.LogRecord;
import com.lab.labsystem.annotation.RequireRole;
import com.lab.labsystem.common.Result;
import com.lab.labsystem.entity.LabRoom;
import com.lab.labsystem.entity.SysSemester;
import com.lab.labsystem.service.AttendanceService;
import com.lab.labsystem.service.TeacherBaseDataService;
import com.lab.labsystem.service.TeacherProjectService;
import com.lab.labsystem.service.TeacherScoreService;
import com.lab.labsystem.service.TeacherService;
import com.lab.labsystem.service.TeacherSessionService;
import com.lab.labsystem.vo.StudentVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Tag(name = "教师端接口", description = "包含实验项目发布、场次排课、成绩管理、数据统计等功能")
@RequireRole("teacher")
@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    @Autowired private TeacherScoreService teacherScoreService;
    @Autowired private TeacherProjectService teacherProjectService;
    @Autowired private TeacherSessionService teacherSessionService;
    @Autowired private AttendanceService attendanceService;
    @Autowired private TeacherBaseDataService teacherBaseDataService;
    @Autowired private TeacherService teacherService;

    // ================= 0. 基础数据 =================
    @Operation(summary = "获取我的教学班列表", description = "用于下拉框选择，返回当前教师负责的所有教学班")
    @GetMapping("/my-classes")
    public Result<List<Map<String, String>>> getMyClasses(HttpServletRequest request) {
        String workNo = (String) request.getAttribute("currentUserNo");
        return Result.success(teacherBaseDataService.listMyClasses(workNo));
    }

    @Operation(summary = "获取实验室列表")
    @GetMapping("/labs")
    public Result<List<LabRoom>> getAllLabs() {
        return Result.success(teacherBaseDataService.listLabs());
    }

    @Operation(summary = "获取学期列表")
    @GetMapping("/semesters")
    public Result<List<SysSemester>> getAllSemesters() {
        return Result.success(teacherBaseDataService.listSemesters());
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
    public Result<String> addProject(@Valid @RequestBody ProjectCreateDTO dto, HttpServletRequest request) {
        String currentWorkNo = (String) request.getAttribute("currentUserNo");
        return Result.success(teacherProjectService.addProject(dto, currentWorkNo));
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

    @Operation(summary = "删除实验项目", description = "如果项目下还有已排课的场次，则拒绝删除")
    @LogRecord("删除实验项目")
    @DeleteMapping("/project/delete")
    public Result<String> deleteProject(@RequestParam String projectNo, HttpServletRequest request) {
        String currentWorkNo = (String) request.getAttribute("currentUserNo");
        return Result.success(teacherProjectService.deleteProject(projectNo, currentWorkNo));
    }

    // ================= 2. 实验场次安排 =================
    @Operation(summary = "安排实验场次", description = "包含教师时间冲突、实验室占用、教学班冲突等多重校验")
    @LogRecord("安排实验场次")
    @PostMapping("/session/add")
    public Result<String> addSession(@Valid @RequestBody SessionCreateDTO dto, HttpServletRequest request) {
        String currentWorkNo = (String) request.getAttribute("currentUserNo");
        return Result.success(teacherSessionService.addSession(dto, currentWorkNo));
    }

    @Operation(summary = "获取我负责的实验场次列表")
    @GetMapping("/my-sessions")
    public Result<Object> getMySessions(
            HttpServletRequest request,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        String workNo = (String) request.getAttribute("currentUserNo");
        return Result.success(teacherSessionService.listMySessions(workNo, page, size));
    }

    // ================= 3. 查看名单 =================
    @Operation(summary = "查看场次预约学生名单")
    @GetMapping("/session-bookings")
    public Result<List<StudentVO>> getSessionBookings(@RequestParam String sessionNo,
                                                      HttpServletRequest request) {
        String currentWorkNo = (String) request.getAttribute("currentUserNo");
        return Result.success(teacherSessionService.listSessionBookings(sessionNo, currentWorkNo));
    }

    @Operation(summary = "取消排课")
    @LogRecord("取消排课")
    @DeleteMapping("/session/delete")
    public Result<String> deleteSession(@RequestParam String sessionNo, HttpServletRequest request) {
        String currentWorkNo = (String) request.getAttribute("currentUserNo");
        return Result.success(teacherSessionService.deleteSession(sessionNo, currentWorkNo));
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
        String workNo = (String) request.getAttribute("currentUserNo");
        return Result.success(teacherService.changePassword(workNo, oldPwd, newPwd));
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