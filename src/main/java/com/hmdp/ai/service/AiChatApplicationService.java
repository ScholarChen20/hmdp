package com.hmdp.ai.service;

import cn.hutool.core.util.StrUtil;
import com.hmdp.ai.config.RedisChatMemoryStore;
import com.hmdp.ai.dto.AiChatResponse;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AiChatApplicationService {
    private final AiCustomerService aiCustomerService;
    private final RedisChatMemoryStore chatMemoryStore;

    public AiChatApplicationService(AiCustomerService aiCustomerService, RedisChatMemoryStore chatMemoryStore) {
        this.aiCustomerService = aiCustomerService;
        this.chatMemoryStore = chatMemoryStore;
    }

    public AiChatResponse chat(String conversationId, String message) {
        if (UserHolder.getUser() == null) {
            throw new IllegalStateException("????????????");
        }
        Long userId = UserHolder.getUser().getId();
        String actualConversationId = StrUtil.isBlank(conversationId)
                ? UUID.randomUUID().toString().replace("-", "") : conversationId;
        String memoryId = userId + ":" + actualConversationId;
        AiRequestContext.setAppointmentConfirmation(isConfirmationMessage(message));
        try {
            return new AiChatResponse(actualConversationId, aiCustomerService.chat(memoryId, message));
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
}
