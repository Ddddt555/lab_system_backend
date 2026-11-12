package com.lab.labsystem.service.teacher;

import com.lab.labsystem.dto.request.ProjectCreateDTO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 教师端实验项目管理。
 *
 * <p>从 {@code TeacherController} 下沉出来的：项目列表（仅限本人教学班）、
 * 发布项目（校验教学班归属）、上传指导书（格式+魔数校验、MinIO 上传与失败回滚）、删除项目。
 */
public interface TeacherProjectService {

    /**
     * 当前教师负责的教学班下的实验项目列表。
     *
     * @return {@code PageResult} 或 {@code List}
     */
    Object listMyProjects(String workNo, Integer page, Integer size);

    /**
     * 发布实验项目。
     *
     * @param dto 包含项目名称、教学班号、学时、考核标准等基本信息
     * @param workNo 当前操作的教师工号，用于校验权限
     * @return 成功提示（含生成的项目号）
     * @throws com.lab.labsystem.common.BusinessException 未选教学班或无权在该班发布
     */
    String addProject(ProjectCreateDTO dto, String workNo);

    /**
     * 上传实验指导书。
     *
     * @return 成功提示
     * @throws com.lab.labsystem.common.BusinessException 文件为空/格式非法/无权操作/上传失败
     */
    String uploadGuide(MultipartFile file, String projectNo, String workNo);

    /**
     * 删除实验项目。
     *
     * @param projectNo 项目号
     * @param workNo 当前操作的教师工号
     * @return 成功提示
     * @throws com.lab.labsystem.common.BusinessException 项目不存在/无权操作/存在关联场次
     */
    String deleteProject(String projectNo, String workNo);
}