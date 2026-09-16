package com.hmdp.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {
    private Qwen qwen = new Qwen();
    private Chat chat = new Chat();
    private Appointment appointment = new Appointment();

    @Data
    public static class Qwen {
        private boolean enabled;
        private String apiKey;
        private String modelName = "qwen-plus";
        private Integer timeoutSeconds = 30;
    }

    @Data
    public static class Chat {
        private Integer memoryMaxMessages = 40;
        private Integer memoryTtlHours = 24;
    }

    @Data
    public static class Appointment {
        private Integer confirmationTtlMinutes = 15;
    }
}
