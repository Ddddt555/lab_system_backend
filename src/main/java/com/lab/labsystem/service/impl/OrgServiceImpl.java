package com.lab.labsystem.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.labsystem.common.PageResult;
import com.lab.labsystem.entity.AdminClass;
import com.lab.labsystem.entity.Department;
import com.lab.labsystem.entity.Major;
import com.lab.labsystem.mapper.AdminClassMapper;
import com.lab.labsystem.mapper.DepartmentMapper;
import com.lab.labsystem.mapper.MajorMapper;
import com.lab.labsystem.service.OrgService;
import com.lab.labsystem.common.NumberGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrgServiceImpl implements OrgService {
    @Autowired private MajorMapper majorMapper;
    @Autowired private AdminClassMapper adminClassMapper;
    @Autowired private DepartmentMapper departmentMapper;
    @Autowired private NumberGenerator numberGenerator;

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
    public Object getMajorList(Integer page, Integer size) {
        List<Major> majors;
        long total = 0;
        if (page != null && size != null) {
            Page<Major> majorPage = majorMapper.selectPage(new Page<>(page, size), null);
            majors = majorPage.getRecords();
            total = majorPage.getTotal();
        } else {
            majors = majorMapper.selectList(null);
        }

        List<Map<String, Object>> resultList = new ArrayList<>();
        for (Major m : majors) {
            Map<String, Object> map = new HashMap<>();
            map.put("majorNo", m.getMajorNo());
            map.put("majorName", m.getMajorName());
            map.put("deptNo", m.getDeptNo());
            Department dept = departmentMapper.selectById(m.getDeptNo());
            map.put("deptName", dept != null ? dept.getDeptName() : "未分配");
            resultList.add(map);
        }
        if (page != null && size != null) return new PageResult<>(total, resultList);
        return resultList;
    }

    @Override
    public String addMajor(Major major) {
        major.setMajorNo(numberGenerator.generateMajorNo());
        majorMapper.insert(major);
        return "专业添加成功！专业号为：" + major.getMajorNo();
    }

    @Override
    public void deleteMajor(String majorNo) {
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
            classes = adminClassMapper.selectList(null);
        }

        List<Map<String, Object>> resultList = new ArrayList<>();
        for (AdminClass ac : classes) {
            Map<String, Object> map = new HashMap<>();
            map.put("adminClassNo", ac.getAdminClassNo());
            map.put("majorNo", ac.getMajorNo());
            map.put("enrollmentYear", ac.getEnrollmentYear());
            map.put("classSeq", ac.getClassSeq());
            Major major = majorMapper.selectById(ac.getMajorNo());
            map.put("majorName", major != null ? major.getMajorName() : "未知专业");
            map.put("adminClassName", buildAdminClassName(ac));
            resultList.add(map);
        }
        if (page != null && size != null) return new PageResult<>(total, resultList);
        return resultList;
    }

    @Override
    public String addAdminClass(AdminClass adminClass) {
        if (adminClass.getMajorNo() == null || adminClass.getEnrollmentYear() == null || adminClass.getClassSeq() == null) {
            throw new RuntimeException("专业、入学年份、班级序号不能为空！");
        }
        Major major = majorMapper.selectById(adminClass.getMajorNo());
        if (major == null) throw new RuntimeException("所选专业不存在！");
        String deptNo = major.getDeptNo();

        String newClassNo = numberGenerator.generateAdminClassNo(adminClass.getEnrollmentYear(), deptNo, adminClass.getMajorNo(), adminClass.getClassSeq());
        if (adminClassMapper.selectById(newClassNo) != null) {
            throw new RuntimeException("该班级已存在，请检查年份、专业和序号！");
        }
        adminClass.setAdminClassNo(newClassNo);
        adminClassMapper.insert(adminClass);
        return "行政班添加成功！班级号为：" + newClassNo;
    }

    @Override
    public void deleteAdminClass(String adminClassNo) {
        adminClassMapper.deleteById(adminClassNo);
    }
}