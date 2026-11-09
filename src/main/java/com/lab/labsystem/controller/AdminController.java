package com.lab.labsystem.controller;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.labsystem.annotation.LogRecord;
import com.lab.labsystem.annotation.RequireRole;
import com.lab.labsystem.common.ExcelUtils;
import com.lab.labsystem.common.NumberGenerator;
import com.lab.labsystem.common.Result;
import com.lab.labsystem.entity.*;
import com.lab.labsystem.mapper.*;
import com.lab.labsystem.service.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@Tag(name = "管理员端接口", description = "包含学生、教师、院系、专业、课程、实验室等基础数据的增删改查及批量操作")
@RequireRole("admin")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired private com.lab.labsystem.service.AdminService adminService;
    @Autowired private LabRoomMapper labRoomMapper;
    @Autowired private ExpCourseMapper courseMapper;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private com.lab.labsystem.service.MustChangePasswordCache mustChangePasswordCache;
    @Autowired private SysLogMapper logMapper;
    @Autowired private NumberGenerator numberGenerator;
    @Autowired private LabEquipmentMapper equipMapper;
    @Autowired private SysSemesterMapper semesterMapper;
    @Autowired private DepartmentMapper departmentMapper;
    @Autowired private TitleDictMapper titleDictMapper;
    @Autowired private StudentMapper studentMapper;
    @Autowired private TeacherMapper teacherMapper;

    @Autowired private StudentService studentService;
    @Autowired private TeacherService teacherService;
    @Autowired private OrgService orgService;
    @Autowired private TeachingService teachingService;

    // ========== 日志管理 ==========
    @Operation(summary = "获取系统操作日志")
    @GetMapping("/logs")
    public Result<Object> getLogs(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        QueryWrapper<SysLog> qw = new QueryWrapper<>();
        qw.orderByDesc("create_time");
        if (page != null && size != null) return Result.success(logMapper.selectPage(new Page<>(page, size), qw));
        // 【安全加固】未传分页参数时默认返回第 1 页，不再整表返回。
        // sys_log 是增长最快的表，原先不带 page/size 就能一次拉走全部数据。
        return Result.success(logMapper.selectPage(new Page<>(1, 100), qw));
    }

    // ========== 学生管理 ==========
    @Operation(summary = "获取学生列表")
    @GetMapping("/students")
    public Result<Object> getAllStudents(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return Result.success(studentService.getStudentList(page, size));
    }

    @Operation(summary = "新增学生")
    @LogRecord("新增学生")
    @PostMapping("/student/add")
    public Result<String> addStudent(@Valid @RequestBody Student student) {
        return Result.success(studentService.addStudent(student));
    }

    @Operation(summary = "删除学生")
    @LogRecord("删除学生")
    @DeleteMapping("/student/delete")
    public Result<String> deleteStudent(@RequestParam String studentNo) {
        studentService.deleteStudent(studentNo);
        return Result.success("学生已删除！");
    }

    // ========== 教师管理 ==========
    @Operation(summary = "获取教师列表")
    @GetMapping("/teachers")
    public Result<Object> getAllTeachers(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return Result.success(teacherService.getTeacherList(page, size));
    }

    @Operation(summary = "新增教师")
    @LogRecord("新增教师")
    @PostMapping("/teacher/add")
    public Result<String> addTeacher(@Valid @RequestBody Teacher teacher) {
        return Result.success(teacherService.addTeacher(teacher));
    }

    @Operation(summary = "删除教师")
    @LogRecord("删除教师")
    @DeleteMapping("/teacher/delete")
    public Result<String> deleteTeacher(@RequestParam String workNo) {
        teacherService.deleteTeacher(workNo);
        return Result.success("教师已删除！");
    }

    // ========== 专业与行政班 ==========
    @Operation(summary = "获取专业列表")
    @GetMapping("/majors")
    public Result<Object> getAllMajors(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return Result.success(orgService.getMajorList(page, size));
    }

    @Operation(summary = "新增专业")
    @LogRecord("新增专业")
    @PostMapping("/major/add")
    public Result<String> addMajor(@Valid @RequestBody Major major) {
        return Result.success(orgService.addMajor(major));
    }

    @Operation(summary = "删除专业")
    @LogRecord("删除专业")
    @DeleteMapping("/major/delete")
    public Result<String> deleteMajor(@RequestParam String majorNo) {
        orgService.deleteMajor(majorNo);
        return Result.success("专业已删除！");
    }

    @Operation(summary = "获取行政班列表")
    @GetMapping("/admin-classes")
    public Result<Object> getAllAdminClasses(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return Result.success(orgService.getAdminClassList(page, size));
    }

    @Operation(summary = "新增行政班")
    @LogRecord("新增行政班")
    @PostMapping("/admin-class/add")
    public Result<String> addAdminClass(@Valid @RequestBody AdminClass adminClass) { // 🛑 加上 @Valid
        return Result.success(orgService.addAdminClass(adminClass));
    }

    @Operation(summary = "删除行政班")
    @LogRecord("删除行政班")
    @DeleteMapping("/admin-class/delete")
    public Result<String> deleteAdminClass(@RequestParam String adminClassNo) {
        orgService.deleteAdminClass(adminClassNo);
        return Result.success("行政班已删除！");
    }

    // ========== 教学班管理 ==========
    @Operation(summary = "获取教学班列表")
    @GetMapping("/teach-classes")
    public Result<Object> getAllTeachClasses(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return Result.success(teachingService.getTeachClassList(page, size));
    }

    @Operation(summary = "创建教学班")
    @LogRecord("创建教学班")
    @PostMapping("/teach-class/add")
    public Result<String> addTeachClass(@Valid @RequestBody TeachClass teachClass) { // 🛑 加上 @Valid
        return Result.success(teachingService.addTeachClass(teachClass));
    }

    @Operation(summary = "删除教学班")
    @LogRecord("删除教学班")
    @DeleteMapping("/teach-class/delete")
    public Result<String> deleteTeachClass(@RequestParam String classNo) {
        teachingService.deleteTeachClass(classNo);
        return Result.success("教学班已删除！");
    }

    // ========== 以下为极简 CRUD，直接保留在 Controller ==========
    
    @GetMapping("/departments")
    public Result<List<Department>> getDepartments() { return Result.success(departmentMapper.selectList(null)); }

    @LogRecord("新增院系")
    @PostMapping("/department/add")
    public Result<String> addDepartment(@Valid @RequestBody Department dept) {
        dept.setDeptNo(numberGenerator.generateDeptNo());
        departmentMapper.insert(dept);
        return Result.success("院系添加成功！院系号为：" + dept.getDeptNo());
    }

    @LogRecord("删除院系")
    @DeleteMapping("/department/delete")
    public Result<String> deleteDepartment(@RequestParam String deptNo) {
        departmentMapper.deleteById(deptNo); return Result.success("院系已删除！");
    }

    @GetMapping("/titles")
    public Result<List<TitleDict>> getTitles() { return Result.success(titleDictMapper.selectList(null)); }

    @LogRecord("新增职称")
    @PostMapping("/title/add")
    public Result<String> addTitle(@Valid @RequestBody TitleDict title) {
        title.setTitleNo(numberGenerator.generateTitleNo());
        titleDictMapper.insert(title);
        return Result.success("职称添加成功！职称号为：" + title.getTitleNo());
    }

    @LogRecord("删除职称")
    @DeleteMapping("/title/delete")
    public Result<String> deleteTitle(@RequestParam String titleNo) {
        titleDictMapper.deleteById(titleNo); return Result.success("职称已删除！");
    }

    @LogRecord("重置用户密码")
    @PostMapping("/reset-password")
    public Result<String> resetPassword(@RequestParam String account, @RequestParam String role) {
        String defaultHash = passwordEncoder.encode("123456");
        if ("student".equals(role)) {
            Student student = studentMapper.selectById(account);
            if (student == null) return Result.error("学生不存在！");
            student.setPassword(defaultHash);
            
            student.setMustChangePassword(1);
            student.setPwdUpdateTime(LocalDateTime.now());
            studentMapper.updateById(student);
            // 【缓存一致性】重置密码后需让缓存立即失效，否则最多 60 秒内仍被判定为"无需改密"
            mustChangePasswordCache.evict("student", account);
            
        } else if ("teacher".equals(role)) {
            Teacher teacher = teacherMapper.selectById(account);
            if (teacher == null) return Result.error("教师不存在！");
            teacher.setPassword(defaultHash);
            
            teacher.setMustChangePassword(1);
            teacher.setPwdUpdateTime(LocalDateTime.now());
            teacherMapper.updateById(teacher);
            // 【缓存一致性】同上
            mustChangePasswordCache.evict("teacher", account);
            
        } else { 
            return Result.error("不支持的角色！"); 
        }
        return Result.success("密码已重置为 123456，该用户下次登录必须修改密码！");
    }

    @PostMapping("/update-password")
    public Result<String> updatePassword(@RequestParam String oldPassword, @RequestParam String newPassword) {
    	 String errorMsg = com.lab.labsystem.common.PasswordValidator.validate(newPassword);
         if (errorMsg != null) return Result.error(errorMsg);
        if (adminService.updatePassword(oldPassword, newPassword)) return Result.success("密码修改成功，请重新登录！");
        return Result.error("原密码错误！");
    }

    @GetMapping("/labs")
    public Result<Object> getAllLabs(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        if (page != null && size != null) return Result.success(labRoomMapper.selectPage(new Page<>(page, size), null));
        return Result.success(labRoomMapper.selectList(null));
    }

    @LogRecord("新增实验室")
    @PostMapping("/lab/add")
    public Result<String> addLab(@Valid @RequestBody LabRoom labRoom) { // 🛑 加上 @Valid
        labRoom.setRoomNo(numberGenerator.generateRoomNo());
        labRoomMapper.insert(labRoom);
        return Result.success("实验室添加成功！实验室号为：" + labRoom.getRoomNo());
    }

    @LogRecord("删除实验室")
    @DeleteMapping("/lab/delete")
    public Result<String> deleteLab(@RequestParam String roomNo) {
        labRoomMapper.deleteById(roomNo); return Result.success("实验室已删除！");
    }

    @GetMapping("/courses")
    public Result<Object> getAllCourses(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        if (page != null && size != null) return Result.success(courseMapper.selectPage(new Page<>(page, size), null));
        return Result.success(courseMapper.selectList(null));
    }

    @LogRecord("新增课程")
    @PostMapping("/course/add")
    public Result<String> addCourse(@Valid @RequestBody ExpCourse course) { // 🛑 加上 @Valid
        course.setCourseNo(numberGenerator.generateCourseNo());
        courseMapper.insert(course);
        return Result.success("课程添加成功！课程号为：" + course.getCourseNo());
    }

    @LogRecord("删除课程")
    @DeleteMapping("/course/delete")
    public Result<String> deleteCourse(@RequestParam String courseNo) {
        courseMapper.deleteById(courseNo); return Result.success("课程已删除！");
    }

    @GetMapping("/equipments")
    public Result<List<LabEquipment>> getAllEquipments() { return Result.success(equipMapper.selectList(null)); }

    @LogRecord("新增设备")
    @PostMapping("/equipment/add")
    public Result<String> addEquipment(@Valid @RequestBody LabEquipment equip) { // 🛑 加上 @Valid
        equip.setEquipNo(numberGenerator.generateEquipNo());
        equipMapper.insert(equip);
        return Result.success("设备添加成功！设备号为：" + equip.getEquipNo());
    }

    @LogRecord("删除设备")
    @DeleteMapping("/equipment/delete")
    public Result<String> deleteEquipment(@RequestParam String equipNo) {
        equipMapper.deleteById(equipNo); return Result.success("设备已删除！");
    }

    @GetMapping("/semesters")
    public Result<Object> getAllSemesters(@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        if (page != null && size != null) return Result.success(semesterMapper.selectPage(new Page<>(page, size), null));
        return Result.success(semesterMapper.selectList(null));
    }

    @LogRecord("新增学期")
    @PostMapping("/semester/add")
    public Result<String> addSemester(@Valid @RequestBody SysSemester semester) {
        semesterMapper.insert(semester); return Result.success("学期添加成功！");
    }

    @LogRecord("删除学期")
    @DeleteMapping("/semester/delete")
    public Result<String> deleteSemester(@RequestParam String semesterName) {
        semesterMapper.deleteById(semesterName); return Result.success("学期已删除！");
    }

    @GetMapping("/student/import-template")
    public void downloadStudentTemplate(HttpServletResponse response) throws IOException {
        ExcelUtils.export(response, "学生导入模板", com.lab.labsystem.dto.StudentImportDTO.class, new ArrayList<>());
    }

    @Operation(summary = "批量导入学生")
    @LogRecord("批量导入学生")
    @PostMapping("/student/import")
    public Result<String> importStudents(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.success(studentService.importStudents(file));
    }
    
    @Operation(summary = "获取已删除学生列表(回收站)")
    @GetMapping("/student/recycle")
    public Result<List<Student>> getDeletedStudents() {
        return Result.success(studentMapper.selectDeletedStudents());
    }

    @Operation(summary = "恢复已删除学生")
    @LogRecord("恢复学生")
    @PostMapping("/student/restore")
    public Result<String> restoreStudent(@RequestParam String studentNo) {
        studentMapper.restoreStudent(studentNo);
        return Result.success("学生已成功恢复！");
    }
    
    @Operation(summary = "获取已删除教师列表(回收站)")
    @GetMapping("/teacher/recycle")
    public Result<List<Teacher>> getDeletedTeachers() {
        return Result.success(teacherMapper.selectDeletedTeachers());
    }

    @Operation(summary = "恢复已删除教师")
    @LogRecord("恢复教师")
    @PostMapping("/teacher/restore")
    public Result<String> restoreTeacher(@RequestParam String workNo) {
        teacherMapper.restoreTeacher(workNo);
        return Result.success("教师已成功恢复！");
    }
}