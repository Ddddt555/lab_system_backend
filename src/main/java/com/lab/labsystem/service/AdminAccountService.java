package com.lab.labsystem.service;

import com.lab.labsystem.entity.Student;
import com.lab.labsystem.entity.Teacher;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

/**
 * 后台账号管理。
 *
 * <p>从 {@code AdminController} 下沉出来的学生/教师账号生命周期操作：
 * 重置为初始密码、回收站查询与恢复、学生导入模板导出。
 *
 * <p>学生的增删改查与批量导入已经在 StudentService / TeacherService 中，
 * 此处只补充那些仍散落在 controller 里的账号操作。
 */
public interface AdminAccountService {

    /**
     * 将指定账号的密码重置为 123456，并置为"下次登录必须改密"。
     *
     * @param role student / teacher
     * @return 成功提示
     * @throws com.lab.labsystem.common.BusinessException 账号不存在或角色不支持
     */
    String resetPassword(String account, String role);

    List<Student> listDeletedStudents();

    String restoreStudent(String studentNo);

    List<Teacher> listDeletedTeachers();

    String restoreTeacher(String workNo);

    /** 导出学生批量导入的 Excel 模板。 */
    void exportStudentImportTemplate(HttpServletResponse response) throws IOException;
}
