package com.lab.labsystem.service;
import com.lab.labsystem.entity.*;
public interface TeachingService {
    Object getTeachClassList(Integer page, Integer size);
    String addTeachClass(TeachClass teachClass);
    void deleteTeachClass(String classNo);
}