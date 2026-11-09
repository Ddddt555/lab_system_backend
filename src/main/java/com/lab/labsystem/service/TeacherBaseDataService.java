package com.lab.labsystem.service;

import com.lab.labsystem.entity.LabRoom;
import com.lab.labsystem.entity.SysSemester;

import java.util.List;
import java.util.Map;

/**
 * 教师端基础数据查询。
 *
 * <p>从 {@code TeacherController} 下沉出来的下拉框数据源：
 * 本人教学班（含展示名与成绩权重）、实验室列表、学期列表。
 */
public interface TeacherBaseDataService {

    /** 当前教师负责的教学班，供下拉框使用。 */
    List<Map<String, String>> listMyClasses(String workNo);

    /** 全部实验室。 */
    List<LabRoom> listLabs();

    /** 全部学期。 */
    List<SysSemester> listSemesters();
}
