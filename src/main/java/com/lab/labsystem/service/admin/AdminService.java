package com.lab.labsystem.service.admin;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lab.labsystem.entity.SysAdmin;
import com.lab.labsystem.common.BusinessException;
import com.lab.labsystem.common.PasswordValidator;
import com.lab.labsystem.mapper.SysAdminMapper;
import com.lab.labsystem.service.auth.MustChangePasswordCache;

@Service
public class AdminService {

    @Autowired
    private SysAdminMapper adminMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder; // 注入
    @Autowired
    private MustChangePasswordCache mustChangePasswordCache;

    public boolean checkLogin(String username, String password) {
        QueryWrapper<SysAdmin> qw = new QueryWrapper<>();
        qw.eq("username", username);
        SysAdmin admin = adminMapper.selectOne(qw);
        // 比对密文
        return admin != null && passwordEncoder.matches(password, admin.getPassword());
    }

    public boolean updatePassword(String oldPassword, String newPassword) {
        String errorMsg = PasswordValidator.validate(newPassword);
        if (errorMsg != null) {
            throw new BusinessException(errorMsg);
        }

        SysAdmin admin = adminMapper.selectOne(new QueryWrapper<SysAdmin>().eq("username", "admin"));
        if (admin != null && passwordEncoder.matches(oldPassword, admin.getPassword())) {
            // 存入新密码的哈希值
            admin.setPassword(passwordEncoder.encode(newPassword));
            
            // 🛑【核心修复】改密成功后，重置强制改密状态和更新时间
            admin.setMustChangePassword(0);
            admin.setPwdUpdateTime(java.time.LocalDateTime.now());
            
            adminMapper.updateById(admin);
            // 【缓存一致性】改密成功后清除强制改密缓存
            mustChangePasswordCache.evict("admin", admin.getUsername());
            return true;
        }
        return false;
    }
}