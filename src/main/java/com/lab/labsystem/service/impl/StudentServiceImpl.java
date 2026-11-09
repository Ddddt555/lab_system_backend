package com.lab.labsystem.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.PageResult;
import com.lab.labsystem.common.PasswordValidator;
import com.lab.labsystem.service.MustChangePasswordCache;
import com.lab.labsystem.entity.AdminClass;
import com.lab.labsystem.entity.Department;
import com.lab.labsystem.entity.Major;
import com.lab.labsystem.entity.Student;
import com.lab.labsystem.mapper.AdminClassMapper;
import com.lab.labsystem.mapper.DepartmentMapper;
import com.lab.labsystem.mapper.MajorMapper;
import com.lab.labsystem.mapper.StudentMapper;
import com.lab.labsystem.service.StudentService;
import com.lab.labsystem.common.NumberGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StudentServiceImpl implements StudentService {

    @Autowired private StudentMapper studentMapper;
    @Autowired private AdminClassMapper adminClassMapper;
    @Autowired private MajorMapper majorMapper;
    @Autowired private DepartmentMapper departmentMapper;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private NumberGenerator numberGenerator;
    @Autowired private MustChangePasswordCache mustChangePasswordCache;

    // 辅助方法搬进来
    private String buildAdminClassName(AdminClass ac) {
        if (ac == null) return "未分配";
        Major major = majorMapper.selectById(ac.getMajorNo());
        String majorName = major != null ? major.getMajorName() : "未知专业";
        String deptName = "";
        if (major != null && major.getDeptNo() != null) {
            Department dept = departmentMapper.selectById(major.getDeptNo());
            if (dept != null) deptName = dept.getDeptName() + "-";
        }
        String yearStr = ac.getEnrollmentYear() != null ? String.valueOf(ac.getEnrollmentYear()).substring(2) : "00";
        String seqStr = ac.getClassSeq() != null ? String.format("%02d", ac.getClassSeq()) : "00";
        return deptName + majorName + "-" + yearStr + seqStr + "班";
    }

    @Override
    public Object getStudentList(Integer page, Integer size) {
        List<Student> students;
        long total = 0;
        if (page != null && size != null) {
            Page<Student> studentPage = studentMapper.selectPage(new Page<>(page, size), null);
            students = studentPage.getRecords();
            total = studentPage.getTotal();
        } else {
            students = studentMapper.selectList(null);
        }

        List<Map<String, Object>> resultList = new ArrayList<>();
        for (Student s : students) {
            Map<String, Object> map = new HashMap<>();
            map.put("studentNo", s.getStudentNo());
            map.put("name", s.getName());
            map.put("adminClassNo", s.getAdminClassNo());
            if (s.getAdminClassNo() != null) {
                AdminClass ac = adminClassMapper.selectById(s.getAdminClassNo());
                if (ac != null) {
                    map.put("adminClassName", buildAdminClassName(ac));
                    Major major = majorMapper.selectById(ac.getMajorNo());
                    map.put("majorName", major != null ? major.getMajorName() : "未知专业");
                } else {
                    map.put("adminClassName", "未知班级"); map.put("majorName", "未知专业");
                }
            } else {
                map.put("adminClassName", "未分配"); map.put("majorName", "未分配");
            }
            resultList.add(map);
        }

        if (page != null && size != null) return new PageResult<>(total, resultList);
        return resultList;
    }

    @Override
    public String addStudent(Student student) {
        student.setStudentNo(numberGenerator.generateStudentNo());
        if (student.getPassword() == null || student.getPassword().isEmpty()) {
            student.setPassword("123456");
        }
        student.setPassword(passwordEncoder.encode(student.getPassword()));

        // ⚠️ 核心改造：利用自定义 SQL 穿透逻辑删除，检查该学号是否已存在（哪怕是已删除状态）
        Student existStudent = studentMapper.selectRealById(student.getStudentNo());
        
        if (existStudent != null) {
            // 如果存在，直接把状态复活，并覆盖更新
            studentMapper.restoreStudent(student.getStudentNo()); // 先把 deleted 置为 0
            student.setDeleted(0); // 实体类也设为 0，防止 MyBatis-Plus 忽略更新
            studentMapper.updateById(student); // 更新其他信息
            return "学号 " + student.getStudentNo() + " 曾被删除，已为您恢复并更新！";
        }

        // 如果不存在，正常插入
        studentMapper.insert(student);
        return "学生添加成功！学号为：" + student.getStudentNo();
    }

    @Override
    public void deleteStudent(String studentNo) {
        // 因为加了 @TableLogic，这里的 deleteById 会自动变成 update set deleted=1
        studentMapper.deleteById(studentNo);
    }

    @Override
    public String importStudents(MultipartFile file) throws IOException {
        List<com.lab.labsystem.dto.StudentImportDTO> list = EasyExcel.read(file.getInputStream())
                .head(com.lab.labsystem.dto.StudentImportDTO.class).sheet().doReadSync();

        int successCount = 0;
        List<String> errorMsgs = new ArrayList<>();

        for (int i = 0; i < list.size(); i++) {
            com.lab.labsystem.dto.StudentImportDTO dto = list.get(i);
            int rowNum = i + 2; // 🛑 核心：Excel 表头占第 1 行，数据从第 2 行开始
            try {
                // 1. 基础校验：姓名
                if (dto.getName() == null || dto.getName().trim().isEmpty()) {
                    throw new RuntimeException("姓名不能为空");
                }
                // 2. 基础校验：行政班
                if (dto.getAdminClassNo() == null || dto.getAdminClassNo().trim().isEmpty()) {
                    throw new RuntimeException("行政班号不能为空");
                }
                // 3. 业务校验：行政班必须存在
                if (adminClassMapper.selectById(dto.getAdminClassNo()) == null) {
                    throw new RuntimeException("行政班号 [" + dto.getAdminClassNo() + "] 不存在");
                }

                Student student = new Student();
                student.setStudentNo(numberGenerator.generateStudentNo());
                student.setName(dto.getName());
                student.setAdminClassNo(dto.getAdminClassNo());
                student.setPassword(passwordEncoder.encode("123456"));
                student.setMustChangePassword(1); // 强制首次登录改密
                studentMapper.insert(student);
                successCount++;

            } catch (Exception e) {
                // 🛑 捕获具体异常，记录行号、姓名和原因
                String errName = dto.getName() != null ? dto.getName() : "空";
                errorMsgs.add("第 " + rowNum + " 行 (" + errName + ") 导入失败：" + e.getMessage());
            }
        }

        // 组装最终返回结果
        StringBuilder result = new StringBuilder();
        result.append("导入完成！成功：").append(successCount).append(" 条，失败：").append(errorMsgs.size()).append(" 条。");
        
        // 如果有失败，把详细信息拼在返回字符串里
        if (!errorMsgs.isEmpty()) {
            result.append("\n\n失败详情：\n").append(String.join("\n", errorMsgs));
        }
        return result.toString();
    }
    @Override
    public String changePassword(String studentNo, String oldPwd, String newPwd) {
        String errorMsg = PasswordValidator.validate(newPwd);
        if (errorMsg != null) {
            throw new BusinessException(errorMsg);
        }

        Student student = studentMapper.selectById(studentNo);
        if (student == null || !passwordEncoder.matches(oldPwd, student.getPassword())) {
            throw new BusinessException("原密码错误！");
        }

        student.setPassword(passwordEncoder.encode(newPwd));
        student.setMustChangePassword(0);
        student.setPwdUpdateTime(LocalDateTime.now());
        studentMapper.updateById(student);

        // 【缓存一致性】改密成功后清除强制改密缓存
        mustChangePasswordCache.evict("student", studentNo);

        return "密码修改成功，请重新登录！";
    }
}
