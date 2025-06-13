package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    private static final long COUNT_BITS = 32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    public long nexId(String keyPrefix) {
        //1. 生成时间戳
        LocalDateTime now =LocalDateTime.now();  //   获取当前时间
        long nowSeconds = now.toEpochSecond(ZoneOffset.UTC);  //  获取秒级时间戳
        long timestamp = nowSeconds - BEGIN_TIMESTAMP;

        //2. 生成序列号
        //2.1 获取当前日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //2.2 自增长
        long count = stringRedisTemplate.opsForValue().increment("icr:"+keyPrefix + ":" + date);

        return timestamp << COUNT_BITS | count;

    }
    public static void main(String[] args) {
        LocalDateTime now = LocalDateTime.now();
    }
}
