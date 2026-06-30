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
    private Ai ai = new Ai();

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

    @Data
    public static class Ai {
        private SiliconFlow siliconflow = new SiliconFlow();

        @Data
        public static class SiliconFlow {
            private String apiKey;
            private String model = "Pro/deepseek-ai/DeepSeek-V3.2";
            private String baseUrl = "https://api.siliconflow.cn/v1";
            private int timeoutSeconds = 15;
        }
    }
}
