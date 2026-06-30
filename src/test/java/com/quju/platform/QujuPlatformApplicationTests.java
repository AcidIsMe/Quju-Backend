package com.quju.platform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("应用上下文加载测试")
class QujuPlatformApplicationTests {
    @Test
    void contextLoads() {
    }
}
