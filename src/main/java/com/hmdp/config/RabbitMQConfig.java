package com.hmdp.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.amqp.core.Queue;

@Configuration
public class RabbitMQConfig {

    /// 定义队列名称
    public static final String SECKILL_ORDER_QUEUE = "queue.seckill.order";
//    public static final String SECKILL_ORDER_EXCHANGE = "exchange.seckill.order";
//    public static final String SECKILL_ORDER_ROUTING_KEY = "routingkey.seckill.order";

    /**
     * 定义队列, 用于接收秒杀订单消息
     * @return
     */
    @Bean
    public Queue seckillOrderQueue() {
        // 第一个参数是队列名，第二个表示是否持久化，第三个表示是否排他，第四个表示是否自动删除
        return new Queue(SECKILL_ORDER_QUEUE, true, false, false);
    }
}
