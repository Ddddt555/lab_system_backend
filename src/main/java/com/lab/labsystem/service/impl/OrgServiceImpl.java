package com.lab.labsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.PageResult;
import com.lab.labsystem.common.QueryLimits;
import com.lab.labsystem.entity.AdminClass;
import com.lab.labsystem.entity.Department;
import com.lab.labsystem.entity.Major;
import com.lab.labsystem.entity.Student;
import com.lab.labsystem.mapper.AdminClassMapper;
import com.lab.labsystem.mapper.DepartmentMapper;
import com.lab.labsystem.mapper.MajorMapper;
import com.lab.labsystem.mapper.StudentMapper;
import com.lab.labsystem.service.OrgService;
import com.lab.labsystem.common.NumberGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class OrgServiceImpl implements OrgService {
    @Autowired private MajorMapper majorMapper;
    @Autowired private AdminClassMapper adminClassMapper;
    @Autowired private DepartmentMapper departmentMapper;
    @Autowired private StudentMapper studentMapper;
    @Autowired private NumberGenerator numberGenerator;

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
    public Object getMajorList(Integer page, Integer size) {
        List<Major> majors;
        long total = 0;
        if (page != null && size != null) {
            Page<Major> majorPage = majorMapper.selectPage(new Page<>(page, size), null);
            majors = majorPage.getRecords();
            total = majorPage.getTotal();
        } else {
            // 无分页参数时，限制最大返回条数，防止全表加载
            majors = QueryLimits.list(majorMapper, null);
        }

        if (majors.isEmpty()) {
            return (page != null && size != null) ? new PageResult<>(total, new ArrayList<>()) : new ArrayList<>();
        }

        // 批量查询院系
        List<String> deptNos = majors.stream()
                .map(Major::getDeptNo)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<String, Department> deptMap = deptNos.isEmpty() ? new HashMap<>() :
                departmentMapper.selectBatchIds(deptNos).stream()
                        .collect(Collectors.toMap(Department::getDeptNo, d -> d, (k1, k2) -> k1));

        List<Map<String, Object>> resultList = new ArrayList<>();
        for (Major m : majors) {
            Map<String, Object> map = new HashMap<>();
            map.put("majorNo", m.getMajorNo());
            map.put("majorName", m.getMajorName());
            map.put("deptNo", m.getDeptNo());
            Department dept = deptMap.get(m.getDeptNo());
            map.put("deptName", dept != null ? dept.getDeptName() : "未分配");
            resultList.add(map);
        }
        return (page != null && size != null) ? new PageResult<>(total, resultList) : resultList;
    }

    @Override
    public String addMajor(Major major) {
        major.setMajorNo(numberGenerator.generateMajorNo());
        majorMapper.insert(major);
        return "专业添加成功！专业号为：" + major.getMajorNo();
    }

    @Override
    public void deleteMajor(String majorNo) {
        // 引用检查：专业下还有行政班时不允许删除
        long classes = adminClassMapper.selectCount(
                new QueryWrapper<AdminClass>().eq("major_no", majorNo));
        if (classes > 0) {
            throw new BusinessException("该专业下还有 " + classes + " 个行政班，请先处理后再删除！");
        }
        majorMapper.deleteById(majorNo);
    }

    @Override
    public Object getAdminClassList(Integer page, Integer size) {
        List<AdminClass> classes;
        long total = 0;
        if (page != null && size != null) {
            Page<AdminClass> classPage = adminClassMapper.selectPage(new Page<>(page, size), null);
            classes = classPage.getRecords();
            total = classPage.getTotal();
        } else {
            // 无分页参数时，限制最大返回条数，防止全表加载
            classes = QueryLimits.list(adminClassMapper, null);
        }

        if (classes.isEmpty()) {
            return (page != null && size != null) ? new PageResult<>(total, new ArrayList<>()) : new ArrayList<>();
        }

        // 1. 批量查询专业
        List<String> majorNos = classes.stream()
                .map(AdminClass::getMajorNo)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<String, Major> majorMap = majorNos.isEmpty() ? new HashMap<>() :
                majorMapper.selectBatchIds(majorNos).stream()
                        .collect(Collectors.toMap(Major::getMajorNo, m -> m, (k1, k2) -> k1));

        // 2. 批量查询院系
        List<String> deptNos = majorMap.values().stream()
                .map(Major::getDeptNo)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<String, Department> deptMap = deptNos.isEmpty() ? new HashMap<>() :
                departmentMapper.selectBatchIds(deptNos).stream()
                        .collect(Collectors.toMap(Department::getDeptNo, d -> d, (k1, k2) -> k1));

        // 3. 组装数据
        List<Map<String, Object>> resultList = new ArrayList<>();
        for (AdminClass ac : classes) {
            Map<String, Object> map = new HashMap<>();
            map.put("adminClassNo", ac.getAdminClassNo());
            map.put("majorNo", ac.getMajorNo());
            map.put("enrollmentYear", ac.getEnrollmentYear());
            map.put("classSeq", ac.getClassSeq());

            Major major = majorMap.get(ac.getMajorNo());
            map.put("majorName", major != null ? major.getMajorName() : "未知专业");

            Department dept = major != null ? deptMap.get(major.getDeptNo()) : null;
            map.put("adminClassName", buildAdminClassName(ac, major, dept));
            
            resultList.add(map);
        }
        return (page != null && size != null) ? new PageResult<>(total, resultList) : resultList;
    }

    @Override
    public String addAdminClass(AdminClass adminClass) {
        if (adminClass.getMajorNo() == null || adminClass.getEnrollmentYear() == null || adminClass.getClassSeq() == null) {
            throw new BusinessException("专业、入学年份、班级序号不能为空！");
        }
        Major major = majorMapper.selectById(adminClass.getMajorNo());
        if (major == null) throw new BusinessException("所选专业不存在！");
        String deptNo = major.getDeptNo();

        String newClassNo = numberGenerator.generateAdminClassNo(adminClass.getEnrollmentYear(), deptNo, adminClass.getMajorNo(), adminClass.getClassSeq());
        if (adminClassMapper.selectById(newClassNo) != null) {
            throw new BusinessException("该班级已存在，请检查年份、专业和序号！");
        }
        adminClass.setAdminClassNo(newClassNo);
        adminClassMapper.insert(adminClass);
        return "行政班添加成功！班级号为：" + newClassNo;
    }

    @Override
    public void deleteAdminClass(String adminClassNo) {
        long students = studentMapper.selectCount(
                new QueryWrapper<Student>().eq("admin_class_no", adminClassNo));
        if (students > 0) {
            throw new BusinessException("该行政班下还有 " + students + " 名学生，请先处理后再删除！");
        }
        adminClassMapper.deleteById(adminClassNo);
    }
}