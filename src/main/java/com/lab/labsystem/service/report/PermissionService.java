package com.lab.labsystem.service.report;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lab.labsystem.entity.ExpProject;
import com.lab.labsystem.entity.ExpReport;
import com.lab.labsystem.entity.StudentTeachClass;
import com.lab.labsystem.entity.TeachClass;
import com.lab.labsystem.mapper.ExpProjectMapper;
import com.lab.labsystem.mapper.ExpReportMapper;
import com.lab.labsystem.mapper.StudentTeachClassMapper;
import com.lab.labsystem.mapper.TeachClassMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PermissionService {

    @Autowired private ExpReportMapper reportMapper;
    @Autowired private ExpProjectMapper projectMapper;
    @Autowired private TeachClassMapper teachClassMapper;
    @Autowired private StudentTeachClassMapper studentTeachClassMapper;

    /**
     * 判断当前请求（通过 JwtInterceptor 注入的用户信息）是否有权访问指定文件
     */
    public boolean hasFilePermission(String fileName, HttpServletRequest request) {
        // 1. 先查是不是实验报告
        ExpReport report = reportMapper.selectOne(new QueryWrapper<ExpReport>().eq("file_url", fileName));
        if (report != null) return canAccessReport(request, report);

        // 2. 再查是不是实验指导书
        ExpProject project = projectMapper.selectOne(new QueryWrapper<ExpProject>().eq("guide_file", fileName));
        if (project != null) return canAccessGuide(request, project);

        return false; // 都没查到，说明是野文件或伪造文件名
    }

    public boolean canAccessReport(HttpServletRequest request, ExpReport report) {
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

    public boolean canAccessGuide(HttpServletRequest request, ExpProject project) {
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
}