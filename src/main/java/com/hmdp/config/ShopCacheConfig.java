package com.hmdp.config;

import com.hmdp.cache.ShopCacheInvalidationListener;
import com.hmdp.utils.RedisConstants;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class ShopCacheConfig {
    @Bean
    public ChannelTopic shopCacheInvalidationTopic() {
        return new ChannelTopic(RedisConstants.SHOP_CACHE_INVALIDATION_TOPIC);
    }

    @Bean
    public RedisMessageListenerContainer shopCacheMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            ShopCacheInvalidationListener listener,
            ChannelTopic shopCacheInvalidationTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listener, shopCacheInvalidationTopic);
        return container;
    }
}
