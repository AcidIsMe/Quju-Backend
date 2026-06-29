package com.quju.platform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "quju")
public class QujuProperties {

    private Jwt jwt = new Jwt();
    private FileStorage files = new FileStorage();

    @Data
    public static class Jwt {
        private String secret;
        private Long accessTokenTtlSeconds = 7200L;
        private Long refreshTokenTtlSeconds = 604800L;
    }

    @Data
    public static class FileStorage {
        private String uploadDir = "uploads";
    }
}
