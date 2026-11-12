package com.lab.labsystem.service.admin;

import com.lab.labsystem.dto.request.AdminClassCreateDTO;
import com.lab.labsystem.dto.request.MajorCreateDTO;

public interface OrgService {
    Object getMajorList(Integer page, Integer size);
    
    // 修改为接收 DTO
    String addMajor(MajorCreateDTO dto);
    
    void deleteMajor(String majorNo);

    Object getAdminClassList(Integer page, Integer size);
    
    // 修改为接收 DTO
    String addAdminClass(AdminClassCreateDTO dto);
    
    void deleteAdminClass(String adminClassNo);
}