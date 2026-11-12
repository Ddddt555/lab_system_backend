package com.lab.labsystem.service.admin.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.NumberGenerator;
import com.lab.labsystem.common.QueryLimits;
import com.lab.labsystem.dto.request.CourseCreateDTO;
import com.lab.labsystem.dto.request.EquipmentCreateDTO;
import com.lab.labsystem.dto.request.LabCreateDTO;
import com.lab.labsystem.dto.request.SemesterCreateDTO;
import com.lab.labsystem.entity.ExpCourse;
import com.lab.labsystem.entity.ExpSession;
import com.lab.labsystem.entity.LabEquipment;
import com.lab.labsystem.entity.LabRoom;
import com.lab.labsystem.entity.SysLog;
import com.lab.labsystem.entity.SysSemester;
import com.lab.labsystem.entity.TeachClass;
import com.lab.labsystem.mapper.ExpCourseMapper;
import com.lab.labsystem.mapper.ExpSessionMapper;
import com.lab.labsystem.mapper.LabEquipmentMapper;
import com.lab.labsystem.mapper.LabRoomMapper;
import com.lab.labsystem.mapper.SysLogMapper;
import com.lab.labsystem.mapper.SysSemesterMapper;
import com.lab.labsystem.mapper.TeachClassMapper;
import com.lab.labsystem.service.admin.AdminResourceService;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminResourceServiceImpl implements AdminResourceService {

    private static final int DEFAULT_LOG_PAGE_SIZE = 100;

    private final LabRoomMapper labRoomMapper;
    private final ExpCourseMapper courseMapper;
    private final LabEquipmentMapper equipMapper;
    private final SysSemesterMapper semesterMapper;
    private final SysLogMapper logMapper;
    private final ExpSessionMapper sessionMapper;
    private final TeachClassMapper teachClassMapper;
    private final NumberGenerator numberGenerator;

    public AdminResourceServiceImpl(LabRoomMapper labRoomMapper,
                                    ExpCourseMapper courseMapper,
                                    LabEquipmentMapper equipMapper,
                                    SysSemesterMapper semesterMapper,
                                    SysLogMapper logMapper,
                                    ExpSessionMapper sessionMapper,
                                    TeachClassMapper teachClassMapper,
                                    NumberGenerator numberGenerator) {
        this.labRoomMapper = labRoomMapper;
        this.courseMapper = courseMapper;
        this.equipMapper = equipMapper;
        this.semesterMapper = semesterMapper;
        this.logMapper = logMapper;
        this.sessionMapper = sessionMapper;
        this.teachClassMapper = teachClassMapper;
        this.numberGenerator = numberGenerator;
    }

    @Override
    public Object listLabs(Integer page, Integer size) {
        if (page != null && size != null) {
            return labRoomMapper.selectPage(new Page<>(page, size), null);
        }
        return QueryLimits.list(labRoomMapper, null);
    }

    @Override
    public String addLab(LabCreateDTO dto) {
        LabRoom labRoom = new LabRoom();
        
        // 1. 安全拷贝
        BeanUtils.copyProperties(dto, labRoom);
        
        // 2. 后端接管敏感字段
        labRoom.setRoomNo(numberGenerator.generateRoomNo());
        labRoom.setDeleted(0);
        
        labRoomMapper.insert(labRoom);
        return "实验室添加成功！实验室号为：" + labRoom.getRoomNo();
    }

    @Override
    public void deleteLab(String roomNo) {
        long sessions = sessionMapper.selectCount(new QueryWrapper<ExpSession>().eq("room_no", roomNo));
        if (sessions > 0) {
            throw new BusinessException("该实验室下还有 " + sessions + " 个实验场次，请先处理后再删除！");
        }
        long equipments = equipMapper.selectCount(new QueryWrapper<LabEquipment>().eq("room_no", roomNo));
        if (equipments > 0) {
            throw new BusinessException("该实验室下还有 " + equipments + " 台设备，请先处理后再删除！");
        }
        labRoomMapper.deleteById(roomNo);
    }

    @Override
    public Object listCourses(Integer page, Integer size) {
        if (page != null && size != null) {
            return courseMapper.selectPage(new Page<>(page, size), null);
        }
        return QueryLimits.list(courseMapper, null);
    }

    @Override
    public String addCourse(CourseCreateDTO dto) {
        ExpCourse course = new ExpCourse();
        
        // 1. 安全拷贝
        BeanUtils.copyProperties(dto, course);
        
        // 2. 后端接管敏感字段
        course.setCourseNo(numberGenerator.generateCourseNo());
        course.setDeleted(0);
        
        courseMapper.insert(course);
        return "课程添加成功！课程号为：" + course.getCourseNo();
    }

    @Override
    public void deleteCourse(String courseNo) {
        long classes = teachClassMapper.selectCount(new QueryWrapper<TeachClass>().eq("course_no", courseNo));
        if (classes > 0) {
            throw new BusinessException("该课程已被 " + classes + " 个教学班使用，请先处理后再删除！");
        }
        courseMapper.deleteById(courseNo);
    }

    @Override
    public List<LabEquipment> listEquipments() {
        return equipMapper.selectList(null);
    }

    @Override
    public String addEquipment(EquipmentCreateDTO dto) {
        LabEquipment equip = new LabEquipment();
        
        // 1. 安全拷贝
        BeanUtils.copyProperties(dto, equip);
        
        // 2. 后端接管敏感字段
        equip.setEquipNo(numberGenerator.generateEquipNo());
        equip.setDeleted(0);
        
        equipMapper.insert(equip);
        return "设备添加成功！设备号为：" + equip.getEquipNo();
    }

    @Override
    public void deleteEquipment(String equipNo) {
        equipMapper.deleteById(equipNo);
    }

    @Override
    public Object listSemesters(Integer page, Integer size) {
        if (page != null && size != null) {
            return semesterMapper.selectPage(new Page<>(page, size), null);
        }
        return QueryLimits.list(semesterMapper, null);
    }

    @Override
    public String addSemester(SemesterCreateDTO dto) {
        SysSemester semester = new SysSemester();
        
        // 1. 安全拷贝
        BeanUtils.copyProperties(dto, semester);
        
        // 2. 后端接管敏感字段（学期主键是 semesterName，无自增编号）
        semester.setDeleted(0);
        
        semesterMapper.insert(semester);
        return "学期添加成功！";
    }

    @Override
    public void deleteSemester(String semesterName) {
        long classes = teachClassMapper.selectCount(new QueryWrapper<TeachClass>().eq("semester", semesterName));
        if (classes > 0) {
            throw new BusinessException("该学期已被 " + classes + " 个教学班使用，请先处理后再删除！");
        }
        long sessions = sessionMapper.selectCount(new QueryWrapper<ExpSession>().eq("semester", semesterName));
        if (sessions > 0) {
            throw new BusinessException("该学期下还有 " + sessions + " 个实验场次，请先处理后再删除！");
        }
        semesterMapper.deleteById(semesterName);
    }

    @Override
    public Object listLogs(Integer page, Integer size) {
        QueryWrapper<SysLog> qw = new QueryWrapper<>();
        qw.orderByDesc("create_time");
        if (page != null && size != null) {
            return logMapper.selectPage(new Page<>(page, size), qw);
        }
        return logMapper.selectPage(new Page<>(1, DEFAULT_LOG_PAGE_SIZE), qw);
    }
}