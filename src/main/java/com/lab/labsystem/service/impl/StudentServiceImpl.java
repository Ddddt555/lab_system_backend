package com.lab.labsystem.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.labsystem.common.PageResult;
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
        int failCount = 0;
        for (com.lab.labsystem.dto.StudentImportDTO dto : list) {
            try {
                Student student = new Student();
                student.setStudentNo(numberGenerator.generateStudentNo());
                student.setName(dto.getName());
                student.setAdminClassNo(dto.getAdminClassNo());
                student.setPassword(passwordEncoder.encode("123456"));
                studentMapper.insert(student);
                successCount++;
            } catch (Exception e) {
                failCount++;
            }
        }
        return "导入完成！成功：" + successCount + " 条，失败：" + failCount + " 条";
    }
}