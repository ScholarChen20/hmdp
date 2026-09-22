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
    private RateLimit rateLimit = new RateLimit();
    private Safety safety = new Safety();

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

    @Data
    public static class RateLimit {
        private Integer userPerMinute = 20;
        private Integer ipPerMinute = 60;
        private Integer globalPerMinute = 600;
    }

    @Data
    public static class Safety {
        private String sensitiveWords = "密码,验证码,银行卡,身份证,token,secret";
        private String injectionPatterns = "ignore previous instructions,system prompt,developer message,越过安全限制,忽略之前的指令";
    }
}
