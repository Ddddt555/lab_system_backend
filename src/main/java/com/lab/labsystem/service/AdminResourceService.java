package com.lab.labsystem.service;

import com.lab.labsystem.entity.ExpCourse;
import com.lab.labsystem.entity.LabEquipment;
import com.lab.labsystem.entity.LabRoom;
import com.lab.labsystem.entity.SysSemester;

import java.util.List;

/**
 * 后台资源维护：实验室、课程、设备、学期，以及系统操作日志查询。
 *
 * <p>从 {@code AdminController} 下沉出来的极简 CRUD + 日志分页。
 */
public interface AdminResourceService {

    Object listLabs(Integer page, Integer size);

    String addLab(LabRoom labRoom);

    void deleteLab(String roomNo);

    Object listCourses(Integer page, Integer size);

    String addCourse(ExpCourse course);

    void deleteCourse(String courseNo);

    List<LabEquipment> listEquipments();

    String addEquipment(LabEquipment equip);

    void deleteEquipment(String equipNo);

    Object listSemesters(Integer page, Integer size);

    String addSemester(SysSemester semester);

    void deleteSemester(String semesterName);

    Object listLogs(Integer page, Integer size);
}
