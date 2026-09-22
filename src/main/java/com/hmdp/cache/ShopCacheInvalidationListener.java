package com.hmdp.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShopCacheInvalidationListener implements MessageListener {
    private final ShopLocalCache localCache;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            Long shopId = Long.valueOf(new String(message.getBody(), StandardCharsets.UTF_8));
            localCache.evict(shopId);
        } catch (Exception e) {
            log.error("商家本地缓存失效消息处理失败", e);
        }
    }
}
