package com.hmdp.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AiAuditLogger {
    public void success(Long userId, String conversationId, String message, long elapsedMs) {
        log.info("ai_audit event=chat status=success userId={} conversationId={} traceId={} messageSummary={} elapsedMs={}",
                userId, conversationId, TraceContext.currentTraceId(), summarize(message), elapsedMs);
    }

    public void failure(Long userId, String conversationId, String message, String errorType, long elapsedMs) {
        log.warn("ai_audit event=chat status=failure userId={} conversationId={} traceId={} messageSummary={} errorType={} elapsedMs={}",
                userId, conversationId, TraceContext.currentTraceId(), summarize(message), errorType, elapsedMs);
    }

    public void fallback(Long userId, String conversationId, String message, long elapsedMs) {
        log.info("ai_audit event=chat status=fallback userId={} conversationId={} traceId={} messageSummary={} elapsedMs={}",
                userId, conversationId, TraceContext.currentTraceId(), summarize(message), elapsedMs);
    }

    private String summarize(String message) {
        if (message == null) return "";
        String value = message.replaceAll("[\\r\\n\\t]", " ").replaceAll("(?i)(password|token|secret|验证码|身份证|银行卡)[^ ]*", "[已脱敏]");
        return value.length() <= 80 ? value : value.substring(0, 80) + "…";
    }
}
