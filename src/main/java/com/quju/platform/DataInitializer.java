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
    }
}
