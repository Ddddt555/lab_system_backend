package com.lab.labsystem.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.lab.labsystem.annotation.RequireRole;
import com.lab.labsystem.common.FileTypeUtils;
import com.lab.labsystem.common.Result;
import com.lab.labsystem.entity.AdminClass;
import com.lab.labsystem.entity.Department;
import com.lab.labsystem.entity.ExpProject;
import com.lab.labsystem.entity.ExpReport;
import com.lab.labsystem.entity.Major;
import com.lab.labsystem.entity.Student;
import com.lab.labsystem.entity.StudentTeachClass;
import com.lab.labsystem.entity.TeachClass;
import com.lab.labsystem.mapper.AdminClassMapper;
import com.lab.labsystem.mapper.DepartmentMapper;
import com.lab.labsystem.mapper.ExpProjectMapper;
import com.lab.labsystem.mapper.ExpReportMapper;
import com.lab.labsystem.mapper.MajorMapper;
import com.lab.labsystem.mapper.StudentMapper;
import com.lab.labsystem.mapper.StudentTeachClassMapper;
import com.lab.labsystem.mapper.TeachClassMapper;
import com.lab.labsystem.service.MinioService;
import com.lab.labsystem.service.TeacherScoreService;
import com.lab.labsystem.vo.ReportVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Tag(name = "实验报告接口", description = "包含学生报告上传、多版本控制、教师批阅、文件安全下载与预览等")
@RestController
@RequestMapping("/api/report")
public class ReportController {

    @Autowired private ExpReportMapper reportMapper;
    @Autowired private StudentMapper studentMapper;
    @Autowired private AdminClassMapper adminClassMapper;
    @Autowired private MajorMapper majorMapper;
    @Autowired private DepartmentMapper departmentMapper;
    @Autowired private ExpProjectMapper projectMapper;
    @Autowired private TeachClassMapper teachClassMapper;
    @Autowired private StudentTeachClassMapper studentTeachClassMapper;
    
    @Autowired private MinioService minioService;
    @Autowired private TeacherScoreService teacherScoreService;
    
    @Autowired private StringRedisTemplate stringRedisTemplate;

    @Value("${app.cpolar-compatible:false}")
    private boolean isCpollarCompatible;

    private static final List<String> ALLOWED_EXTENSIONS =
            Arrays.asList(".pdf", ".doc", ".docx", ".zip", ".rar");

    // ========== 1. 学生上传报告 ==========
    @Operation(summary = "学生上传实验报告", description = "支持多版本，上传后自动将旧版本标记为历史记录，当前版本为最新版。")
    @RequireRole("student")
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/upload")
    // 🛑【核心修复：移除了 public synchronized】🛑
    // 原因：synchronized 会锁死整个实例，导致多个人同时上传时被迫排队，性能极差。且多台服务器部署时会失效。
    // 改用 Redis 分布式锁，只锁定当前上传者+项目，互不干扰。
    public Result<String> uploadReport( 
            @RequestParam("file") MultipartFile file,
            @RequestParam("projectNo") String projectNo,
            HttpServletRequest request) {

        String studentNo = (String) request.getAttribute("currentUserNo");
        if (file.isEmpty()) return Result.error("上传的文件不能为空！");

        String originalFilename = file.getOriginalFilename();
        String suffix = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            suffix = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }
        if (!ALLOWED_EXTENSIONS.contains(suffix)) {
            return Result.error("文件格式非法！只允许上传 PDF、Word、ZIP、RAR 格式。");
        }
        
     // 🛑【新增：魔数深度校验】🛑
        // 防止黑客将木马文件改名为 .pdf 上传
        if (!FileTypeUtils.isValidFile(file, suffix)) {
            return Result.error("文件内容与后缀不符，非法文件已被拦截！");
        }

        ExpProject project = projectMapper.selectById(projectNo);
        if (project == null) return Result.error("实验项目不存在！");

        // 分布式锁 Key：锁住当前学生对该项目的上传操作
        String lockKey = "lock:report:upload:" + studentNo + ":" + projectNo;
        // 尝试获取锁，有效期 5 秒（防止死锁）
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 5, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(locked)) {
            return Result.error("您有报告正在上传中，请勿频繁点击！");
        }

        String newFileName = UUID.randomUUID().toString().replace("-", "") + suffix;
        boolean minioUploadSuccess = false;

        try {
            // 1. 先上传文件到 MinIO（耗时操作在锁的保护下进行，防止并发覆盖版本）
            minioService.upload(file, newFileName);
            minioUploadSuccess = true;

            // 2. 更新旧版本状态
            UpdateWrapper<ExpReport> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("student_no", studentNo)
                         .eq("project_no", projectNo)
                         .set("is_latest", 0);
            reportMapper.update(null, updateWrapper);
            
            // 3. 清理被逻辑删除的历史废弃记录，防止唯一键冲突
            reportMapper.deleteOldSoftDeletedReports(studentNo, projectNo);

            // 4. 查最大版本号
            QueryWrapper<ExpReport> qw = new QueryWrapper<>();
            qw.eq("student_no", studentNo).eq("project_no", projectNo)
              .orderByDesc("version").last("limit 1");
            ExpReport lastReport = reportMapper.selectOne(qw);
            int newVersion = (lastReport != null) ? lastReport.getVersion() + 1 : 1;

            // 5. 插入新版本记录
            ExpReport report = new ExpReport();
            report.setReportNo("R" + UUID.randomUUID().toString().substring(0, 8));
            report.setStudentNo(studentNo);
            report.setProjectNo(projectNo);
            report.setFileUrl(newFileName);
            report.setVersion(newVersion);
            report.setIsLatest(1);
            report.setSubmitTime(LocalDateTime.now());
            reportMapper.insert(report);

            return Result.success("报告上传成功，当前为第 " + newVersion + " 版");

        } catch (DuplicateKeyException e) {
            System.err.println("版本号冲突，并发插入被唯一键拦截: " + newFileName);
            if (minioUploadSuccess) minioService.remove(newFileName);
            return Result.error("网络开小差了，请稍后重试！");
        } catch (Exception e) {
            System.err.println("数据库写入失败，开始清理 MinIO 孤儿文件: " + newFileName);
            if (minioUploadSuccess) minioService.remove(newFileName);
            throw new RuntimeException("数据库保存失败，文件已清理，请重试！", e);
        } finally {
            // 🛑【核心修复】无论成功还是失败，必须释放锁！
            stringRedisTemplate.delete(lockKey);
        }
    }

    // ========== 2. 动态获取 URL (双轨制) ==========
    @Operation(summary = "获取下载链接")
    @GetMapping("/download-url/{fileName}")
    public Result<String> getDownloadUrl(@PathVariable String fileName, HttpServletRequest request) {
        if (!hasPermission(fileName, request)) return Result.error("没有权限！");
        String baseUrl = request.getScheme() + "://" + request.getServerName() + 
                         (request.getServerPort() == 80 || request.getServerPort() == 443 ? "" : ":" + request.getServerPort());
        
        if (isCpollarCompatible) {
            String token = request.getHeader("Authorization").replace("Bearer ", "");
            return Result.success(baseUrl + "/api/report/download/" + fileName + "?token=" + token);
        } else {
            String ticket = UUID.randomUUID().toString().replace("-", "");
            String userNo = (String) request.getAttribute("currentUserNo");
            String role = (String) request.getAttribute("currentUserRole");
            stringRedisTemplate.opsForValue().set("report:ticket:" + ticket, userNo + ":" + role, 30, TimeUnit.SECONDS);
            return Result.success(baseUrl + "/api/report/download/" + fileName + "?ticket=" + ticket);
        }
    }

    @Operation(summary = "获取预览链接")
    @GetMapping("/preview-url/{fileName}")
    public Result<String> getPreviewUrl(@PathVariable String fileName, HttpServletRequest request) {
        if (!hasPermission(fileName, request)) return Result.error("没有权限！");
        String baseUrl = request.getScheme() + "://" + request.getServerName() + 
                         (request.getServerPort() == 80 || request.getServerPort() == 443 ? "" : ":" + request.getServerPort());
        
        if (isCpollarCompatible) {
            String token = request.getHeader("Authorization").replace("Bearer ", "");
            return Result.success(baseUrl + "/api/report/preview/" + fileName + "?token=" + token);
        } else {
            String ticket = UUID.randomUUID().toString().replace("-", "");
            String userNo = (String) request.getAttribute("currentUserNo");
            String role = (String) request.getAttribute("currentUserRole");
            stringRedisTemplate.opsForValue().set("report:ticket:" + ticket, userNo + ":" + role, 30, TimeUnit.SECONDS);
            return Result.success(baseUrl + "/api/report/preview/" + fileName + "?ticket=" + ticket);
        }
    }

    private boolean hasPermission(String fileName, HttpServletRequest request) {
        ExpReport report = reportMapper.selectOne(new QueryWrapper<ExpReport>().eq("file_url", fileName));
        if (report != null) return canAccessReport(request, report);
        
        ExpProject project = projectMapper.selectOne(new QueryWrapper<ExpProject>().eq("guide_file", fileName));
        if (project != null) return canAccessGuide(request, project);
        
        return false;
    }

    // ========== 3. 后端内部代理获取文件流 ==========
    @GetMapping("/download/{fileName}")
    public void downloadFile(@PathVariable String fileName, HttpServletResponse response) {
        streamFromMinio(fileName, response, false);
    }

    @GetMapping("/preview/{fileName}")
    public void previewFile(@PathVariable String fileName, HttpServletResponse response) {
        streamFromMinio(fileName, response, true);
    }

    private void streamFromMinio(String fileName, HttpServletResponse response, boolean inline) {
        try (java.io.InputStream is = minioService.getObjectStream(fileName)) {
            if (inline) {
                response.setContentType("application/pdf");
                response.setHeader("Content-Disposition", "inline; filename=\"" + fileName + "\"");
            } else {
                response.setContentType("application/octet-stream");
                response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
            }
            org.springframework.util.StreamUtils.copy(is, response.getOutputStream());
            response.flushBuffer();
        } catch (Exception e) {
            e.printStackTrace();
            response.setStatus(500);
        }
    }

    // ========== 4. 权限校验辅助方法 ==========
    private boolean canAccessReport(HttpServletRequest request, ExpReport report) {
        String role = (String) request.getAttribute("currentUserRole");
        String userNo = (String) request.getAttribute("currentUserNo");
        if (role == null || userNo == null) return false;

        if ("admin".equals(role)) return true;
        if ("student".equals(role)) return userNo.equals(report.getStudentNo());
        if ("teacher".equals(role)) {
            ExpProject p = projectMapper.selectById(report.getProjectNo());
            if (p == null) return false;
            TeachClass tc = teachClassMapper.selectById(p.getClassNo());
            return tc != null && userNo.equals(tc.getWorkNo());
        }
        return false;
    }

    private boolean canAccessGuide(HttpServletRequest request, ExpProject project) {
        String role = (String) request.getAttribute("currentUserRole");
        String userNo = (String) request.getAttribute("currentUserNo");
        if (role == null || userNo == null) return false;

        if ("admin".equals(role)) return true;
        if ("teacher".equals(role)) {
            TeachClass tc = teachClassMapper.selectById(project.getClassNo());
            return tc != null && userNo.equals(tc.getWorkNo());
        }
        if ("student".equals(role)) {
            return studentTeachClassMapper.selectCount(
                    new QueryWrapper<StudentTeachClass>()
                            .eq("student_no", userNo)
                            .eq("class_no", project.getClassNo())) > 0;
        }
        return false;
    }

    // ========== 5. 教师批阅 ==========
    @Operation(summary = "教师批阅报告")
    @RequireRole("teacher")
    @PostMapping("/grade")
    @Transactional(rollbackFor = Exception.class)
    public Result<String> gradeReport(
            @RequestParam String reportNo,
            @RequestParam Double score,
            @RequestParam(required = false) String comment,
            HttpServletRequest request) {

        String currentWorkNo = (String) request.getAttribute("currentUserNo");
        if (score == null || score < 0 || score > 100) {
            return Result.error("分数必须在 0-100 之间！");
        }

        ExpReport report = reportMapper.selectById(reportNo);
        if (report == null) return Result.error("找不到该报告！");

        ExpProject project = projectMapper.selectById(report.getProjectNo());
        if (project == null) return Result.error("报告所属项目不存在！");

        TeachClass tc = teachClassMapper.selectById(project.getClassNo());
        if (tc == null || !currentWorkNo.equals(tc.getWorkNo())) {
            return Result.error("非法操作：您无权批阅该报告！");
        }

        report.setScore(score);
        report.setComment(comment);
        reportMapper.updateById(report);

        teacherScoreService.recalculateAfterGrade(report.getStudentNo(), project.getClassNo());

        return Result.success("批阅成功，系统已自动更新该学生的报告均分和总评！");
    }

    // ========== 6. 报告历史 ==========
    @Operation(summary = "获取我的报告版本历史")
    @RequireRole("student")
    @GetMapping("/my-versions")
    public Result<List<ExpReport>> getMyVersions(@RequestParam String projectNo, HttpServletRequest request) {
        String studentNo = (String) request.getAttribute("currentUserNo");
        QueryWrapper<ExpReport> qw = new QueryWrapper<>();
        qw.eq("student_no", studentNo)
          .eq("project_no", projectNo)
          .orderByDesc("version");
        return Result.success(reportMapper.selectList(qw));
    }

    // ========== 7. 教师端项目报告列表 ==========
    @Operation(summary = "获取项目下所有提交的报告")
    @RequireRole("teacher")
    @GetMapping("/list-by-project")
    public Result<List<ReportVO>> getReportsByProject(@RequestParam String projectNo, HttpServletRequest request) {
        String currentWorkNo = (String) request.getAttribute("currentUserNo");

        ExpProject project = projectMapper.selectById(projectNo);
        if (project == null) return Result.error("实验项目不存在！");

        TeachClass tc = teachClassMapper.selectById(project.getClassNo());
        if (tc == null || !currentWorkNo.equals(tc.getWorkNo())) {
            return Result.error("非法操作：您无权查看该项目报告！");
        }

        QueryWrapper<ExpReport> qw = new QueryWrapper<>();
        qw.eq("project_no", projectNo).eq("is_latest", 1);
        List<ExpReport> reportList = reportMapper.selectList(qw);

        if (reportList.isEmpty()) return Result.success(new ArrayList<>());

        // 🛑【核心优化】批量查询学生、行政班、专业、院系，消灭 N+1
        List<String> studentNos = reportList.stream().map(ExpReport::getStudentNo).distinct().collect(Collectors.toList());
        Map<String, Student> studentMap = studentMapper.selectBatchIds(studentNos).stream()
                .collect(Collectors.toMap(Student::getStudentNo, s -> s, (k1, k2) -> k1));

        List<String> adminClassNos = studentMap.values().stream().map(Student::getAdminClassNo).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<String, AdminClass> adminClassMap = adminClassNos.isEmpty() ? new HashMap<>() :
                adminClassMapper.selectBatchIds(adminClassNos).stream()
                .collect(Collectors.toMap(AdminClass::getAdminClassNo, ac -> ac, (k1, k2) -> k1));

        List<String> majorNos = adminClassMap.values().stream().map(AdminClass::getMajorNo).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<String, Major> majorMap = majorNos.isEmpty() ? new HashMap<>() :
                majorMapper.selectBatchIds(majorNos).stream()
                .collect(Collectors.toMap(Major::getMajorNo, m -> m, (k1, k2) -> k1));

        List<String> deptNos = majorMap.values().stream().map(Major::getDeptNo).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<String, Department> deptMap = deptNos.isEmpty() ? new HashMap<>() :
                departmentMapper.selectBatchIds(deptNos).stream()
                .collect(Collectors.toMap(Department::getDeptNo, d -> d, (k1, k2) -> k1));

        List<ReportVO> resultList = new ArrayList<>();
        for (ExpReport report : reportList) {
            ReportVO vo = new ReportVO();
            BeanUtils.copyProperties(report, vo);

            Student student = studentMap.get(report.getStudentNo());
            if (student != null) {
                vo.setStudentName(student.getName());
                if (student.getAdminClassNo() != null) {
                    AdminClass ac = adminClassMap.get(student.getAdminClassNo());
                    if (ac != null) {
                        Major major = majorMap.get(ac.getMajorNo());
                        String majorName = major != null ? major.getMajorName() : "未知专业";
                        
                        String deptName = "";
                        if (major != null && major.getDeptNo() != null) {
                            Department dept = deptMap.get(major.getDeptNo());
                            if (dept != null) deptName = dept.getDeptName() + "-";
                        }
                        
                        String yearStr = ac.getEnrollmentYear() != null
                                ? String.valueOf(ac.getEnrollmentYear()).substring(2) : "00";
                        String seqStr = ac.getClassSeq() != null
                                ? String.format("%02d", ac.getClassSeq()) : "00";
                        vo.setClassName(deptName + majorName + "-" + yearStr + seqStr + "班");
                    } else {
                        vo.setClassName(student.getAdminClassNo());
                    }
                } else {
                    vo.setClassName("未分班");
                }
            }
            resultList.add(vo);
        }
        return Result.success(resultList);
    }
}