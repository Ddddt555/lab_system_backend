package com.lab.labsystem.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lab.labsystem.entity.SysAdmin;
import com.lab.labsystem.mapper.SysAdminMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Slf4j
@Component
public class AdminInitializer implements ApplicationRunner {

    @Autowired
    private SysAdminMapper adminMapper;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Value("${app.admin.init-password:}")
    private String initPassword;

    @Override
    public void run(ApplicationArguments args) {
        Long count = adminMapper.selectCount(new QueryWrapper<SysAdmin>().eq("username", "admin"));
        if (count == 0) {
            String finalPassword = initPassword;
            boolean isRandom = false;

            if (!StringUtils.hasText(finalPassword)) {
                finalPassword = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
                isRandom = true;
            }

            SysAdmin admin = new SysAdmin();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode(finalPassword));
            admin.setMustChangePassword(1);
            admin.setDeleted(0);
            adminMapper.insert(admin);

            if (isRandom) {
                log.warn("==================================================");
                log.warn("系统检测到首次部署，已自动生成 admin 管理员账号！");
                log.warn("初始密码为: {}", finalPassword);
                log.warn("请立即登录并修改密码！此密码只在本次启动时打印。");
                log.warn("==================================================");
            } else {
                log.info("已使用环境变量中的 ADMIN_INIT_PASSWORD 初始化管理员账号。");
            }
        }
    }
}