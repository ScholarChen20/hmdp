package com.hmdp.ai.audit;

import com.hmdp.ai.dto.AiChatResponse;
import com.hmdp.ai.service.AiRequestContext;
import com.hmdp.ai.service.AiAuditLogger;
import com.hmdp.ai.service.TraceContext;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class AiAuditAspect {
    private final AiAuditLogger aiAuditLogger;

    @Around("@annotation(aiAudit)")
    public Object audit(ProceedingJoinPoint joinPoint, AiAudit aiAudit) throws Throwable {
        long startedAt = System.nanoTime();
        Object[] args = joinPoint.getArgs();
        String message = stringArg(args, aiAudit.messageIndex());
        String conversationId = stringArg(args, aiAudit.conversationIdIndex());
        Long userId = UserHolder.getUser() == null ? null : UserHolder.getUser().getId();

        try {
            Object result = joinPoint.proceed();
            String status = AiRequestContext.isFallback() ? "fallback" : "success";
            AiAuditEvent event = AiAuditEvent.builder()
                    .operation(aiAudit.operation())
                    .status(status)
                    .userId(userId)
                    .conversationId(resolveConversationId(result, conversationId))
                    .traceId(TraceContext.currentTraceId())
                    .message(message)
                    .answer(answerOf(result))
                    .errorType(AiRequestContext.getFallbackErrorType())
                    .elapsedMs(elapsedMs(startedAt))
                    .build();
            aiAuditLogger.log(event);
            return result;
        } catch (Throwable error) {
            AiAuditEvent event = AiAuditEvent.builder()
                    .operation(aiAudit.operation())
                    .status("failure")
                    .userId(userId)
                    .conversationId(conversationId)
                    .traceId(TraceContext.currentTraceId())
                    .message(message)
                    .errorType(error.getClass().getSimpleName())
                    .elapsedMs(elapsedMs(startedAt))
                    .build();
            aiAuditLogger.log(event);
            throw error;
        } finally {
            AiRequestContext.clear();
        }
    }

    private String stringArg(Object[] args, int index) {
        if (index < 0 || index >= args.length || args[index] == null) return null;
        return String.valueOf(args[index]);
    }

    private String resolveConversationId(Object result, String fallback) {
        if (result instanceof AiChatResponse) {
            return ((AiChatResponse) result).getConversationId();
        }
        return fallback;
    }

    private String answerOf(Object result) {
        return result instanceof AiChatResponse ? ((AiChatResponse) result).getAnswer() : null;
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
