package com.hmdp.ai.config;

import cn.hutool.core.util.StrUtil;
import com.hmdp.ai.service.AiCustomerService;
import com.hmdp.ai.tools.AppointmentAiTools;
import com.hmdp.ai.tools.ShopAiTools;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.dashscope.QwenChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "ai.qwen", name = "enabled", havingValue = "true")
    public ChatLanguageModel qwenChatLanguageModel(AiProperties properties) {
        if (StrUtil.isBlank(properties.getQwen().getApiKey())) {
            throw new IllegalStateException("QWEN_ENABLED=true ????? QWEN_API_KEY");
        }
        return QwenChatModel.builder()
                .apiKey(properties.getQwen().getApiKey())
                .modelName(properties.getQwen().getModelName())
                .maxTokens(1024)
                .build();
    }

    @Bean
    public AiCustomerService aiCustomerService(ObjectProvider<ChatLanguageModel> modelProvider,
                                                RedisChatMemoryStore chatMemoryStore,
                                                AiProperties properties,
                                                ShopAiTools shopAiTools,
                                                AppointmentAiTools appointmentAiTools) {
        ChatLanguageModel qwenChatLanguageModel = modelProvider.getIfAvailable();
        if (qwenChatLanguageModel == null) {
            return (memoryId, message) -> {
                throw new IllegalStateException("???????????? QWEN_ENABLED=true ? QWEN_API_KEY");
            };
        }
        return AiServices.builder(AiCustomerService.class)
                .chatLanguageModel(qwenChatLanguageModel)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(properties.getChat().getMemoryMaxMessages())
                        .chatMemoryStore(chatMemoryStore)
                        .build())
                .tools(shopAiTools, appointmentAiTools)
                .build();
    }
}
