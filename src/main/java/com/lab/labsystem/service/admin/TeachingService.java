package com.lab.labsystem.service.admin;

import com.lab.labsystem.dto.request.TeachClassCreateDTO;

public interface TeachingService {
    Object getTeachClassList(Integer page, Integer size);
    
    // 修改为接收 DTO
    String addTeachClass(TeachClassCreateDTO dto);
    
    void deleteTeachClass(String classNo);
}