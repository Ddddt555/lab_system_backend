package com.lab.labsystem.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lab.labsystem.entity.SysAdmin;
import com.lab.labsystem.mapper.SysAdminMapper;

@Service
public class AdminService {

    @Autowired
    private SysAdminMapper adminMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder; // 注入

    public boolean checkLogin(String username, String password) {
        QueryWrapper<SysAdmin> qw = new QueryWrapper<>();
        qw.eq("username", username);
        SysAdmin admin = adminMapper.selectOne(qw);
        // 比对密文
        return admin != null && passwordEncoder.matches(password, admin.getPassword());
    }

    public boolean updatePassword(String oldPassword, String newPassword) {
        SysAdmin admin = adminMapper.selectOne(new QueryWrapper<SysAdmin>().eq("username", "admin"));
        if (admin != null && passwordEncoder.matches(oldPassword, admin.getPassword())) {
            // 存入新密码的哈希值
            admin.setPassword(passwordEncoder.encode(newPassword));
            adminMapper.updateById(admin);
            return true;
        }
        return false;
    }
}