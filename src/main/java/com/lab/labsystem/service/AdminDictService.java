package com.lab.labsystem.service;

import com.lab.labsystem.entity.Department;
import com.lab.labsystem.entity.TitleDict;

import java.util.List;

/**
 * 后台字典维护：院系、职称。
 *
 * <p>从 {@code AdminController} 下沉出来的极简 CRUD。
 * 这类实体只有"列表 / 新增（自动生成编号）/ 删除"，单独归组便于以后
 * 统一加上"删除前引用检查"。
 */
public interface AdminDictService {

    List<Department> listDepartments();

    String addDepartment(Department dept);

    void deleteDepartment(String deptNo);

    List<TitleDict> listTitles();

    String addTitle(TitleDict title);

    void deleteTitle(String titleNo);
}
