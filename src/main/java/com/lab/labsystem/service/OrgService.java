package com.lab.labsystem.service;
import com.lab.labsystem.entity.*;
public interface OrgService {
    Object getMajorList(Integer page, Integer size);
    String addMajor(Major major);
    void deleteMajor(String majorNo);

    Object getAdminClassList(Integer page, Integer size);
    String addAdminClass(AdminClass adminClass);
    void deleteAdminClass(String adminClassNo);
}