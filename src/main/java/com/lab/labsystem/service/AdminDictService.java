package com.lab.labsystem.service;

import com.lab.labsystem.dto.request.DepartmentCreateDTO;
import com.lab.labsystem.dto.request.TitleCreateDTO;
import com.lab.labsystem.entity.Department;
import com.lab.labsystem.entity.TitleDict;

import java.util.List;

public interface AdminDictService {

    List<Department> listDepartments();

    // 修改为接收 DTO
    String addDepartment(DepartmentCreateDTO dto);

    void deleteDepartment(String deptNo);

    List<TitleDict> listTitles();

    // 修改为接收 DTO
    String addTitle(TitleCreateDTO dto);

    void deleteTitle(String titleNo);
}