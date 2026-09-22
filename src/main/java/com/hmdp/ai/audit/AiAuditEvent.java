package com.hmdp.ai.audit;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AiAuditEvent {
    String operation;
    String status;
    Long userId;
    String conversationId;
    String traceId;
    String message;
    String answer;
    String errorType;
    long elapsedMs;
}
