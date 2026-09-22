package com.hmdp.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hmdp.entity.Shop;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class ShopLocalCache {
    private final Cache<Long, Shop> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();

    public Shop get(Long id) {
        return cache.getIfPresent(id);
    }

    public void put(Long id, Shop shop) {
        if (id != null && shop != null) {
            cache.put(id, shop);
        }
    }

    public void evict(Long id) {
        if (id != null) {
            cache.invalidate(id);
        }
    }
}
