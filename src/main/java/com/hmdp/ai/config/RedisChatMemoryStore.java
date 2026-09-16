package com.hmdp.ai.config;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class RedisChatMemoryStore implements ChatMemoryStore {
    private static final String KEY_PREFIX = "ai:chat:memory:";

    private final StringRedisTemplate stringRedisTemplate;
    private final AiProperties aiProperties;

    public RedisChatMemoryStore(StringRedisTemplate stringRedisTemplate, AiProperties aiProperties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.aiProperties = aiProperties;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String value = stringRedisTemplate.opsForValue().get(key(memoryId));
        return value == null || value.isEmpty() ? Collections.emptyList()
                : ChatMessageDeserializer.messagesFromJson(value);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        stringRedisTemplate.opsForValue().set(key(memoryId), ChatMessageSerializer.messagesToJson(messages),
                aiProperties.getChat().getMemoryTtlHours(), TimeUnit.HOURS);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        stringRedisTemplate.delete(key(memoryId));
    }

    private String key(Object memoryId) {
        return KEY_PREFIX + memoryId;
    }
}
