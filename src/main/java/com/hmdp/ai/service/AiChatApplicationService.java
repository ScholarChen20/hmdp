package com.hmdp.ai.service;

import cn.hutool.core.util.StrUtil;
import com.hmdp.ai.config.RedisChatMemoryStore;
import com.hmdp.ai.dto.AiChatResponse;
import com.hmdp.ai.exception.AiRequestRejectedException;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AiChatApplicationService {
    private final AiCustomerService aiCustomerService;
    private final RedisChatMemoryStore chatMemoryStore;
    private final AiRateLimiter aiRateLimiter;
    private final com.hmdp.ai.security.AiSafetyGuard aiSafetyGuard;
    private final AiFaqService aiFaqService;
    private final AiAuditLogger aiAuditLogger;
    private final AiMetrics aiMetrics;

    public AiChatApplicationService(AiCustomerService aiCustomerService, RedisChatMemoryStore chatMemoryStore,
                                    AiRateLimiter aiRateLimiter,
                                    com.hmdp.ai.security.AiSafetyGuard aiSafetyGuard,
                                    AiFaqService aiFaqService, AiAuditLogger aiAuditLogger, AiMetrics aiMetrics) {
        this.aiCustomerService = aiCustomerService;
        this.chatMemoryStore = chatMemoryStore;
        this.aiRateLimiter = aiRateLimiter;
        this.aiSafetyGuard = aiSafetyGuard;
        this.aiFaqService = aiFaqService;
        this.aiAuditLogger = aiAuditLogger;
        this.aiMetrics = aiMetrics;
    }

    public AiChatResponse chat(String conversationId, String message) {
        return chat(conversationId, message, "unknown");
    }

    public AiChatResponse chat(String conversationId, String message, String clientIp) {
        if (UserHolder.getUser() == null) {
            throw new IllegalStateException("????????????");
        }
        Long userId = UserHolder.getUser().getId();
        String actualConversationId = StrUtil.isBlank(conversationId)
                ? UUID.randomUUID().toString().replace("-", "") : conversationId;
        String memoryId = userId + ":" + actualConversationId;
        long startedAt = System.nanoTime();
        aiMetrics.request();
        try {
            aiRateLimiter.check(userId, clientIp);
        } catch (AiRequestRejectedException e) {
            aiMetrics.rateLimited();
            aiAuditLogger.failure(userId, actualConversationId, message, e.getClass().getSimpleName(), elapsedMs(startedAt));
            throw e;
        }
        try {
            aiSafetyGuard.validateInput(message);
        } catch (AiRequestRejectedException e) {
            aiMetrics.safetyRejected();
            aiAuditLogger.failure(userId, actualConversationId, message, e.getClass().getSimpleName(), elapsedMs(startedAt));
            throw e;
        }
        AiRequestContext.setAppointmentConfirmation(isConfirmationMessage(message));
        try {
            String answer;
            boolean fallback = false;
            try {
                answer = aiCustomerService.chat(memoryId, message);
            } catch (RuntimeException e) {
                answer = aiFaqService.answer(message).orElse("当前智能客服暂时不可用，请稍后重试或联系人工客服");
                aiMetrics.failure();
                fallback = !answer.startsWith("当前智能客服");
                if (fallback) {
                    aiMetrics.faqFallback();
                    aiAuditLogger.fallback(userId, actualConversationId, message, elapsedMs(startedAt));
                }
                aiAuditLogger.failure(userId, actualConversationId, message, e.getClass().getSimpleName(), elapsedMs(startedAt));
            }
            answer = aiSafetyGuard.sanitizeOutput(answer);
            if (!fallback) {
                aiAuditLogger.success(userId, actualConversationId, message, elapsedMs(startedAt));
            }
            return new AiChatResponse(actualConversationId, answer);
        } finally {
            AiRequestContext.clear();
        }
    }

    public void clearConversation(String conversationId) {
        if (UserHolder.getUser() == null) {
            throw new IllegalStateException("??????????");
        }
        chatMemoryStore.deleteMessages(UserHolder.getUser().getId() + ":" + conversationId);
    }

    private boolean isConfirmationMessage(String message) {
        String normalized = message == null ? "" : message.trim().replaceAll("\\s+", "");
        return "????".equals(normalized) || "??".equals(normalized) || "????".equals(normalized);
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
