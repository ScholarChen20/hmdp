package com.hmdp.config;


import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient() {
        // 配置类
        Config config = new Config();
        // 设置redis地址
        config.useSingleServer().setAddress("redis://192.168.100.128:6379").setPassword("123321");
        // 创建Redisson客户端
        return Redisson.create(config);
    }
}
