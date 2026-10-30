package com.lab.labsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lab.labsystem.entity.StudentBooking;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StudentBookingMapper extends BaseMapper<StudentBooking> {
}