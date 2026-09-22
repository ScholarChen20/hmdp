package com.hmdp.ai.service;

import com.hmdp.ai.audit.AiAuditEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AiAuditLogger {
    public void log(AiAuditEvent event) {
        String template = "ai_audit event={} status={} userId={} conversationId={} traceId={} messageSummary={} answerSummary={} errorType={} elapsedMs={}";
        Object[] values = {event.getOperation(), event.getStatus(), event.getUserId(), event.getConversationId(),
                event.getTraceId(), summarize(event.getMessage()), summarize(event.getAnswer()), event.getErrorType(),
                event.getElapsedMs()};
        if ("failure".equals(event.getStatus())) {
            log.warn(template, values);
        } else {
            log.info(template, values);
        }
    }

    private String summarize(String message) {
        if (message == null) return "";
        String value = message.replaceAll("[\\r\\n\\t]", " ").replaceAll("(?i)(password|token|secret|验证码|身份证|银行卡)[^ ]*", "[已脱敏]");
        return value.length() <= 80 ? value : value.substring(0, 80) + "…";
    }
}
