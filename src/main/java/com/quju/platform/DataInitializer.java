package com.quju.platform;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.entity.UserEntity;
import com.quju.platform.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        String adminEmail = "admin@quju.com";
        UserEntity existing = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getEmail, adminEmail));
        if (existing == null) {
            UserEntity admin = new UserEntity();
            admin.setEmail(adminEmail);
            admin.setPasswordHash(passwordEncoder.encode("Admin12345"));
            admin.setNickname("AdminMaster");
            admin.setRole("admin");
            admin.setStatus("active");
            admin.setCreditScore(100);
            admin.setInterestTags(List.of());
            userMapper.insert(admin);
            System.out.println("[DataInitializer] Admin created: admin@quju.com / Admin12345 (role=admin)");
        } else if (!"admin".equals(existing.getRole()) || !"active".equals(existing.getStatus())) {
            existing.setRole("admin");
            existing.setStatus("active");
            userMapper.updateById(existing);
            System.out.println("[DataInitializer] Admin fixed: role=admin, status=active");
        }

        // 开发环境：创建已激活的普通用户用于小程序测试
        String testEmail = "test@quju.com";
        UserEntity testUser = userMapper.selectOne(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getEmail, testEmail));
        if (testUser == null) {
            UserEntity user = new UserEntity();
            user.setEmail(testEmail);
            user.setPasswordHash(passwordEncoder.encode("Test12345"));
            user.setNickname("测试用户");
            user.setRole("personal");
            user.setStatus("active");
            user.setCreditScore(100);
            user.setInterestTags(List.of("户外", "桌游"));
            userMapper.insert(user);
            System.out.println("[DataInitializer] Test user created: test@quju.com / Test12345 (role=personal, active)");
        } else if (!"active".equals(testUser.getStatus())) {
            testUser.setStatus("active");
            userMapper.updateById(testUser);
            System.out.println("[DataInitializer] Test user fixed: status=active");
        }
    }
}
