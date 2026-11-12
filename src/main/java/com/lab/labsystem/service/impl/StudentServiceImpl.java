package com.lab.labsystem.service.impl;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.PageResult;
import com.lab.labsystem.common.PasswordValidator;
import com.lab.labsystem.common.QueryLimits;
import com.lab.labsystem.dto.request.StudentCreateDTO;
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
import org.springframework.beans.BeanUtils;
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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StudentServiceImpl implements StudentService {

    @Autowired private StudentMapper studentMapper;
    @Autowired private AdminClassMapper adminClassMapper;
    @Autowired private MajorMapper majorMapper;
    @Autowired private DepartmentMapper departmentMapper;
    @Autowired private BCryptPasswordEncoder passwordEncoder;
    @Autowired private NumberGenerator numberGenerator;
    @Autowired private MustChangePasswordCache mustChangePasswordCache;

    // 辅助方法：去除了查库逻辑，完全依赖传入的对象
    private String buildAdminClassName(AdminClass ac, Major major, Department dept) {
        if (ac == null) return "未分配";
        String majorName = major != null ? major.getMajorName() : "未知专业";
        String deptName = "";
        if (dept != null) {
            deptName = dept.getDeptName() + "-";
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
            // 无分页参数时，限制最大返回条数，防止全表加载
            students = QueryLimits.list(studentMapper, null);
        }

        if (students.isEmpty()) {
            return (page != null && size != null) ? new PageResult<>(total, new ArrayList<>()) : new ArrayList<>();
        }

        // 1. 批量查询行政班
        List<String> adminClassNos = students.stream()
                .map(Student::getAdminClassNo)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<String, AdminClass> adminClassMap = adminClassNos.isEmpty() ? new HashMap<>() :
                adminClassMapper.selectBatchIds(adminClassNos).stream()
                        .collect(Collectors.toMap(AdminClass::getAdminClassNo, ac -> ac, (k1, k2) -> k1));

        // 2. 批量查询专业
        List<String> majorNos = adminClassMap.values().stream()
                .map(AdminClass::getMajorNo)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<String, Major> majorMap = majorNos.isEmpty() ? new HashMap<>() :
                majorMapper.selectBatchIds(majorNos).stream()
                        .collect(Collectors.toMap(Major::getMajorNo, m -> m, (k1, k2) -> k1));

        // 3. 批量查询院系
        List<String> deptNos = majorMap.values().stream()
                .map(Major::getDeptNo)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<String, Department> deptMap = deptNos.isEmpty() ? new HashMap<>() :
                departmentMapper.selectBatchIds(deptNos).stream()
                        .collect(Collectors.toMap(Department::getDeptNo, d -> d, (k1, k2) -> k1));

        // 4. 组装数据（全从 Map 取，零查库）
        List<Map<String, Object>> resultList = new ArrayList<>();
        for (Student s : students) {
            Map<String, Object> map = new HashMap<>();
            map.put("studentNo", s.getStudentNo());
            map.put("name", s.getName());
            map.put("adminClassNo", s.getAdminClassNo());

            if (s.getAdminClassNo() != null) {
                AdminClass ac = adminClassMap.get(s.getAdminClassNo());
                if (ac != null) {
                    Major major = majorMap.get(ac.getMajorNo());
                    Department dept = major != null ? deptMap.get(major.getDeptNo()) : null;
                    map.put("adminClassName", buildAdminClassName(ac, major, dept));
                    map.put("majorName", major != null ? major.getMajorName() : "未知专业");
                } else {
                    map.put("adminClassName", "未知班级");
                    map.put("majorName", "未知专业");
                }
            } else {
                map.put("adminClassName", "未分配");
                map.put("majorName", "未分配");
            }
            resultList.add(map);
        }

        return (page != null && size != null) ? new PageResult<>(total, resultList) : resultList;
    }

    @Override
    public String addStudent(StudentCreateDTO dto) {
        Student student = new Student();
        
        // 1. 安全拷贝：只拷贝 dto 中的 name/adminClassNo
        BeanUtils.copyProperties(dto, student);

        // 2. 后端接管敏感字段
        student.setStudentNo(numberGenerator.generateStudentNo());
        student.setPassword(passwordEncoder.encode("123456")); // 强制默认密码
        student.setMustChangePassword(1); // 强制首次修改密码
        student.setDeleted(0); // 默认未删除

        // 3. 利用自定义 SQL 穿透逻辑删除，检查该学号是否已存在（哪怕是已删除状态）
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

        if (list.isEmpty()) {
            return "Excel 文件为空！";
        }

        // 1. 提取所有需要校验的班级号
        List<String> classNosToCheck = list.stream()
                .map(com.lab.labsystem.dto.StudentImportDTO::getAdminClassNo)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(no -> !no.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        // 2. 一次性查出库中存在的班级，放入 Set 方便 O(1) 比对
        Set<String> existingClassNos = classNosToCheck.isEmpty() ? new java.util.HashSet<>() :
                adminClassMapper.selectBatchIds(classNosToCheck).stream()
                        .map(AdminClass::getAdminClassNo)
                        .collect(Collectors.toSet());

        int successCount = 0;
        List<String> errorMsgs = new ArrayList<>();

        for (int i = 0; i < list.size(); i++) {
            com.lab.labsystem.dto.StudentImportDTO dto = list.get(i);
            int rowNum = i + 2; // Excel 表头占第 1 行，数据从第 2 行开始
            try {
                // 基础校验：姓名
                if (dto.getName() == null || dto.getName().trim().isEmpty()) {
                    throw new BusinessException("姓名不能为空");
                }
                // 基础校验：行政班
                if (dto.getAdminClassNo() == null || dto.getAdminClassNo().trim().isEmpty()) {
                    throw new BusinessException("行政班号不能为空");
                }
                // 业务校验：直接比对内存中的 Set，不再查库
                String classNo = dto.getAdminClassNo().trim();
                if (!existingClassNos.contains(classNo)) {
                    throw new BusinessException("行政班号 [" + classNo + "] 不存在");
                }

                Student student = new Student();
                student.setStudentNo(numberGenerator.generateStudentNo());
                student.setName(dto.getName());
                student.setAdminClassNo(classNo);
                student.setPassword(passwordEncoder.encode("123456"));
                student.setMustChangePassword(1); // 强制首次登录改密
                student.setDeleted(0);
                studentMapper.insert(student);
                successCount++;

            } catch (Exception e) {
                String errName = dto.getName() != null ? dto.getName() : "空";
                errorMsgs.add("第 " + rowNum + " 行 (" + errName + ") 导入失败：" + e.getMessage());
            }
        }

        StringBuilder result = new StringBuilder();
        result.append("导入完成！成功：").append(successCount).append(" 条，失败：").append(errorMsgs.size()).append(" 条。");
        
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

        // 缓存一致性：改密成功后清除强制改密缓存
        mustChangePasswordCache.evict("student", studentNo);

        return "密码修改成功，请重新登录！";
    }
}