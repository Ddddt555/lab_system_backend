package com.lab.labsystem.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.lab.labsystem.annotation.RequireRole;
import com.lab.labsystem.common.Result;
import com.lab.labsystem.entity.ExpReport;
import com.lab.labsystem.service.MinioService;
import com.lab.labsystem.service.PermissionService;
import com.lab.labsystem.service.ReportService;
import com.lab.labsystem.vo.ReportVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * 实验报告接口。
 *
 * <p>保留的是 HTTP 层职责：从 JWT 取身份、文件访问鉴权、URL 拼装、响应流写出。
 * 业务逻辑（上传与版本控制、批阅与成绩重算、列表组装、票据签发）在 {@link ReportService}。
 */
@Tag(name = "实验报告接口", description = "包含学生报告上传、多版本控制、教师批阅、文件安全下载与预览等")
@RestController
@RequestMapping("/api/report")
@Slf4j
public class ReportController {

    @Autowired private ReportService reportService;
    @Autowired private PermissionService permissionService;
    @Autowired private MinioService minioService;

    @Value("${app.cpolar-compatible:false}")
    private boolean isCpollarCompatible;

    // ========== 1. 学生上传报告 ==========
    @Operation(summary = "学生上传实验报告", description = "支持多版本，上传后自动将旧版本标记为历史记录，当前版本为最新版。")
    @RequireRole("student")
    @PostMapping("/upload")
    public Result<String> uploadReport(
            @RequestParam("file") MultipartFile file,
            @RequestParam("projectNo") String projectNo,
            HttpServletRequest request) {

        String studentNo = (String) request.getAttribute("currentUserNo");
        return Result.success(reportService.upload(file, projectNo, studentNo));
    }

    // ========== 2. 动态获取 URL (双轨制) ==========
    @Operation(summary = "获取下载链接")
    @GetMapping("/download-url/{fileName}")
    public Result<String> getDownloadUrl(@PathVariable String fileName, HttpServletRequest request) {
        return buildFileUrl(fileName, "download", request);
    }

    @Operation(summary = "获取预览链接")
    @GetMapping("/preview-url/{fileName}")
    public Result<String> getPreviewUrl(@PathVariable String fileName, HttpServletRequest request) {
        return buildFileUrl(fileName, "preview", request);
    }

    /**
     * 生成带凭证的文件访问地址。
     *
     * <p>两种模式：
     * <ul>
     *   <li>cpolar 兼容模式：直接把 JWT 拼进 URL（会进访问日志，仅用于演示环境）；</li>
     *   <li>常规模式：签发 30 秒一次性 ticket，且绑定文件名，泄露也难以复用。</li>
     * </ul>
     */
    private Result<String> buildFileUrl(String fileName, String action, HttpServletRequest request) {
        if (!permissionService.hasFilePermission(fileName, request)) {
            return Result.error("没有权限！");
        }

        String baseUrl = request.getScheme() + "://" + request.getServerName()
                + (request.getServerPort() == 80 || request.getServerPort() == 443
                        ? "" : ":" + request.getServerPort());

        if (isCpollarCompatible) {
            String token = request.getHeader("Authorization").replace("Bearer ", "");
            return Result.success(baseUrl + "/api/report/" + action + "/" + fileName + "?token=" + token);
        }

        String ticket = reportService.createFileTicket(fileName,
                (String) request.getAttribute("currentUserNo"),
                (String) request.getAttribute("currentUserRole"));
        return Result.success(baseUrl + "/api/report/" + action + "/" + fileName + "?ticket=" + ticket);
    }

    // ========== 3. 后端内部代理获取文件流 ==========
    @GetMapping("/download/{fileName}")
    public void downloadFile(@PathVariable String fileName, HttpServletResponse response, HttpServletRequest request) {
        streamFromMinio(fileName, response, false, request);
    }

    @GetMapping("/preview/{fileName}")
    public void previewFile(@PathVariable String fileName, HttpServletResponse response, HttpServletRequest request) {
        streamFromMinio(fileName, response, true, request);
    }

    private void streamFromMinio(String fileName, HttpServletResponse response, boolean inline, HttpServletRequest request) {
        String ticketFileName = (String) request.getAttribute("ticketFileName");
        if (ticketFileName != null && !ticketFileName.equals(fileName)) {
            log.warn("⚠️ 拦截非法越权：Ticket 绑定文件={}, 尝试下载={}", ticketFileName, fileName);
            response.setStatus(403);
            return;
        }

        if (ticketFileName == null && !permissionService.hasFilePermission(fileName, request)) {
            response.setStatus(403);
            return;
        }

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
            log.error("❌ 读取 MinIO 文件流失败: {}", fileName, e);
            response.setStatus(500);
        }
    }

    // ========== 5. 教师批阅 ==========
    @Operation(summary = "教师批阅报告")
    @RequireRole("teacher")
    @PostMapping("/grade")
    public Result<String> gradeReport(
            @RequestParam String reportNo,
            @RequestParam Double score,
            @RequestParam(required = false) String comment,
            HttpServletRequest request) {

        String currentWorkNo = (String) request.getAttribute("currentUserNo");
        reportService.grade(reportNo, score, comment, currentWorkNo);
        return Result.success("批阅成功，系统已自动更新该学生的报告均分和总评！");
    }

    // ========== 6. 报告历史 ==========
    @Operation(summary = "获取我的报告版本历史")
    @RequireRole("student")
    @GetMapping("/my-versions")
    public Result<List<ExpReport>> getMyVersions(@RequestParam String projectNo, HttpServletRequest request) {
        String studentNo = (String) request.getAttribute("currentUserNo");
        return Result.success(reportService.listVersions(studentNo, projectNo));
    }

    // ========== 7. 教师端项目报告列表 ==========
    @Operation(summary = "获取项目下所有提交的报告")
    @RequireRole("teacher")
    @GetMapping("/list-by-project")
    public Result<List<ReportVO>> getReportsByProject(@RequestParam String projectNo, HttpServletRequest request) {
        String currentWorkNo = (String) request.getAttribute("currentUserNo");
        return Result.success(reportService.listByProject(projectNo, currentWorkNo));
    }
}
