package com.lab.labsystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lab.labsystem.common.NumberGenerator;
import com.lab.labsystem.common.QueryLimits;
import com.lab.labsystem.entity.ExpCourse;
import com.lab.labsystem.entity.LabEquipment;
import com.lab.labsystem.entity.LabRoom;
import com.lab.labsystem.entity.SysLog;
import com.lab.labsystem.entity.SysSemester;
import com.lab.labsystem.mapper.ExpCourseMapper;
import com.lab.labsystem.mapper.LabEquipmentMapper;
import com.lab.labsystem.mapper.LabRoomMapper;
import com.lab.labsystem.mapper.SysLogMapper;
import com.lab.labsystem.mapper.SysSemesterMapper;
import com.lab.labsystem.service.AdminResourceService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminResourceServiceImpl implements AdminResourceService {

    /** 日志未传分页参数时的默认每页条数 */
    private static final int DEFAULT_LOG_PAGE_SIZE = 100;

    private final LabRoomMapper labRoomMapper;
    private final ExpCourseMapper courseMapper;
    private final LabEquipmentMapper equipMapper;
    private final SysSemesterMapper semesterMapper;
    private final SysLogMapper logMapper;
    private final NumberGenerator numberGenerator;

    public AdminResourceServiceImpl(LabRoomMapper labRoomMapper,
                                    ExpCourseMapper courseMapper,
                                    LabEquipmentMapper equipMapper,
                                    SysSemesterMapper semesterMapper,
                                    SysLogMapper logMapper,
                                    NumberGenerator numberGenerator) {
        this.labRoomMapper = labRoomMapper;
        this.courseMapper = courseMapper;
        this.equipMapper = equipMapper;
        this.semesterMapper = semesterMapper;
        this.logMapper = logMapper;
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
    public String addLab(LabRoom labRoom) {
        labRoom.setRoomNo(numberGenerator.generateRoomNo());
        labRoomMapper.insert(labRoom);
        return "实验室添加成功！实验室号为：" + labRoom.getRoomNo();
    }

    @Override
    public void deleteLab(String roomNo) {
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
    public String addCourse(ExpCourse course) {
        course.setCourseNo(numberGenerator.generateCourseNo());
        courseMapper.insert(course);
        return "课程添加成功！课程号为：" + course.getCourseNo();
    }

    @Override
    public void deleteCourse(String courseNo) {
        courseMapper.deleteById(courseNo);
    }

    @Override
    public List<LabEquipment> listEquipments() {
        return equipMapper.selectList(null);
    }

    @Override
    public String addEquipment(LabEquipment equip) {
        equip.setEquipNo(numberGenerator.generateEquipNo());
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
    public String addSemester(SysSemester semester) {
        semesterMapper.insert(semester);
        return "学期添加成功！";
    }

    @Override
    public void deleteSemester(String semesterName) {
        semesterMapper.deleteById(semesterName);
    }

    @Override
    public Object listLogs(Integer page, Integer size) {
        QueryWrapper<SysLog> qw = new QueryWrapper<>();
        qw.orderByDesc("create_time");
        if (page != null && size != null) {
            return logMapper.selectPage(new Page<>(page, size), qw);
        }
        // 【安全加固】未传分页参数时默认第 1 页，不再整表返回。
        // sys_log 是增长最快的表，原先不带 page/size 就能一次拉走全部数据。
        return logMapper.selectPage(new Page<>(1, DEFAULT_LOG_PAGE_SIZE), qw);
    }
}
