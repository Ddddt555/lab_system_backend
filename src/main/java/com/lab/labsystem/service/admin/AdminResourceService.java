package com.lab.labsystem.service.admin;

import com.lab.labsystem.dto.request.CourseCreateDTO;
import com.lab.labsystem.dto.request.EquipmentCreateDTO;
import com.lab.labsystem.dto.request.LabCreateDTO;
import com.lab.labsystem.dto.request.SemesterCreateDTO;
import com.lab.labsystem.entity.LabEquipment;

import java.util.List;

public interface AdminResourceService {

    Object listLabs(Integer page, Integer size);

    // 修改为接收 DTO
    String addLab(LabCreateDTO dto);

    void deleteLab(String roomNo);

    Object listCourses(Integer page, Integer size);

    // 修改为接收 DTO
    String addCourse(CourseCreateDTO dto);

    void deleteCourse(String courseNo);

    List<LabEquipment> listEquipments();

    // 修改为接收 DTO
    String addEquipment(EquipmentCreateDTO dto);

    void deleteEquipment(String equipNo);

    Object listSemesters(Integer page, Integer size);

    // 修改为接收 DTO
    String addSemester(SemesterCreateDTO dto);

    void deleteSemester(String semesterName);

    Object listLogs(Integer page, Integer size);
}