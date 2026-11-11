package com.lab.labsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.FileTypeUtils;
import com.lab.labsystem.common.NumberGenerator;
import com.lab.labsystem.common.PageResult;
import com.lab.labsystem.common.QueryLimits;
import com.lab.labsystem.entity.ExpProject;
import com.lab.labsystem.entity.ExpSession;
import com.lab.labsystem.entity.TeachClass;
import com.lab.labsystem.mapper.ExpProjectMapper;
import com.lab.labsystem.mapper.ExpSessionMapper;
import com.lab.labsystem.mapper.TeachClassMapper;
import com.lab.labsystem.service.MinioService;
import com.lab.labsystem.service.TeacherProjectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TeacherProjectServiceImpl implements TeacherProjectService {

    private static final List<String> ALLOWED_EXTENSIONS =
            Arrays.asList(".pdf", ".doc", ".docx", ".zip", ".rar");

    private final TeachClassMapper teachClassMapper;
    private final ExpProjectMapper projectMapper;
    private final ExpSessionMapper sessionMapper;
    private final MinioService minioService;
    private final NumberGenerator numberGenerator;

    public TeacherProjectServiceImpl(TeachClassMapper teachClassMapper,
                                     ExpProjectMapper projectMapper,
                                     ExpSessionMapper sessionMapper,
                                     MinioService minioService,
                                     NumberGenerator numberGenerator) {
        this.teachClassMapper = teachClassMapper;
        this.projectMapper = projectMapper;
        this.sessionMapper = sessionMapper;
        this.minioService = minioService;
        this.numberGenerator = numberGenerator;
    }

    @Override
    public Object listMyProjects(String workNo, Integer page, Integer size) {
        boolean paged = page != null && size != null;

        List<TeachClass> myClasses = teachClassMapper.selectList(
                new QueryWrapper<TeachClass>().eq("work_no", workNo));
        if (myClasses.isEmpty()) {
            return paged ? new PageResult<>(0, new ArrayList<>()) : new ArrayList<>();
        }

        List<String> myClassNos = myClasses.stream()
                .map(TeachClass::getClassNo).collect(Collectors.toList());

        QueryWrapper<ExpProject> qw = new QueryWrapper<ExpProject>()
                .in("class_no", myClassNos)
                .orderByDesc("project_no");

        if (paged) {
            Page<ExpProject> projectPage = projectMapper.selectPage(new Page<>(page, size), qw);
            return new PageResult<>(projectPage.getTotal(), projectPage.getRecords());
        }
        return QueryLimits.list(projectMapper, qw);
    }

    @Override
    public String addProject(ExpProject project, String workNo) {
        if (project.getClassNo() == null || project.getClassNo().isEmpty()) {
            throw new BusinessException("请选择所属教学班！");
        }

        TeachClass tc = teachClassMapper.selectById(project.getClassNo());
        if (tc == null || !workNo.equals(tc.getWorkNo())) {
            throw new BusinessException("非法操作：您无权在该教学班下发布项目！");
        }

        project.setProjectNo(numberGenerator.generateProjectNo());
        projectMapper.insert(project);
        return "项目发布成功！项目号为：" + project.getProjectNo();
    }

    @Override
    public String uploadGuide(MultipartFile file, String projectNo, String workNo) {
        if (file.isEmpty()) {
            throw new BusinessException("文件不能为空！");
        }

        ExpProject project = projectMapper.selectById(projectNo);
        if (project == null) {
            throw new BusinessException("项目不存在！");
        }

        TeachClass tc = teachClassMapper.selectById(project.getClassNo());
        if (tc == null || !workNo.equals(tc.getWorkNo())) {
            throw new BusinessException("非法操作：您无权操作该项目！");
        }

        String originalFilename = file.getOriginalFilename();
        String suffix = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase() : "";
        if (!ALLOWED_EXTENSIONS.contains(suffix)) {
            throw new BusinessException("文件格式非法！");
        }

        // 魔数深度校验：防止把可执行文件改名成 .pdf 上传
        if (!FileTypeUtils.isValidFile(file, suffix)) {
            throw new BusinessException("文件内容与后缀不符，非法文件已被拦截！");
        }

        String newFileName = UUID.randomUUID().toString().replace("-", "") + suffix;

        try {
            minioService.upload(file, newFileName);
        } catch (Exception e) {
            log.error("❌ 上传指导书到 MinIO 失败: {}", newFileName, e);
            throw new BusinessException("上传指导书到对象存储失败，请重试！");
        }

        try {
            project.setGuideFile(newFileName);
            projectMapper.updateById(project);
            return "指导书上传成功！";
        } catch (Exception e) {
            // 数据库失败时清理刚上传的文件，避免产生孤儿对象
            minioService.remove(newFileName);
            throw new RuntimeException("数据库更新失败，请重试！", e);
        }
    }

    @Override
    public String deleteProject(String projectNo, String workNo) {
        ExpProject project = projectMapper.selectById(projectNo);
        if (project == null) {
            throw new BusinessException("实验项目不存在！");
        }

        TeachClass tc = teachClassMapper.selectById(project.getClassNo());
        if (tc == null || !workNo.equals(tc.getWorkNo())) {
            throw new BusinessException("非法操作：您无权删除该项目！");
        }

        long sessionCount = sessionMapper.selectCount(
                new QueryWrapper<ExpSession>().eq("project_no", projectNo)
        );
        if (sessionCount > 0) {
            throw new BusinessException("该项目下还有 " + sessionCount + " 个实验场次，请先取消排课后再删除！");
        }

        projectMapper.deleteById(projectNo);

        if (project.getGuideFile() != null && !project.getGuideFile().isEmpty()) {
            try {
                minioService.remove(project.getGuideFile());
            } catch (Exception e) {
                log.warn("删除项目指导书文件失败，交由定时任务清理: {}", project.getGuideFile());
            }
        }

        return "实验项目删除成功！";
    }
}