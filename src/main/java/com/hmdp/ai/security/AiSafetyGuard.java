package com.hmdp.ai.security;

import com.hmdp.ai.config.AiProperties;
import com.hmdp.ai.exception.AiRequestRejectedException;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;

@Component
public class AiSafetyGuard {
    private final AiProperties properties;

    public AiSafetyGuard(AiProperties properties) {
        this.properties = properties;
    }

    public void validateInput(String message) {
        String normalized = normalize(message);
        if (containsConfiguredWord(normalized, properties.getSafety().getSensitiveWords())) {
            throw new AiRequestRejectedException("问题包含敏感信息，请不要发送密码、验证码或其他隐私数据");
        }
        if (containsConfiguredWord(normalized, properties.getSafety().getInjectionPatterns())) {
            throw new AiRequestRejectedException("问题包含不安全指令，无法执行该请求");
        }
    }

    public String sanitizeOutput(String answer) {
        if (answer == null) {
            return "暂时无法生成回答，请稍后重试";
        }
        String sanitized = answer.replaceAll("(?i)(password|token|secret|验证码|身份证|银行卡)\\s*[:：=]\\s*[^\\s,，。；;]+", "[已脱敏]");
        return sanitized.length() > 4000 ? sanitized.substring(0, 4000) + "…" : sanitized;
    }

    private boolean containsConfiguredWord(String text, String configured) {
        if (configured == null) return false;
        return Arrays.stream(configured.split(","))
                .map(this::normalize)
                .filter(value -> !value.isEmpty())
                .anyMatch(text::contains);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
