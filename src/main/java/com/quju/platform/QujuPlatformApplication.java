package com.quju.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.quju.platform.mapper")
@SpringBootApplication
@EnableScheduling
public class QujuPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(QujuPlatformApplication.class, args);
    }
}
