package com.hmdp.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {
    public static final String SECKILL_ORDER_EXCHANGE = "exchange.seckill.order";
    public static final String SECKILL_ORDER_ROUTING_KEY = "seckill.order";
    public static final String SECKILL_ORDER_QUEUE = "queue.seckill.order";
    public static final String SECKILL_ORDER_RETRY_EXCHANGE = "exchange.seckill.order.retry";
    public static final String SECKILL_ORDER_RETRY_ROUTING_KEY = "seckill.order.retry";
    public static final String SECKILL_ORDER_RETRY_5S_QUEUE = "queue.seckill.order.retry.5s";
    public static final String SECKILL_ORDER_RETRY_30S_QUEUE = "queue.seckill.order.retry.30s";
    public static final String SECKILL_ORDER_DLX = "exchange.seckill.order.dlx";
    public static final String SECKILL_ORDER_DEAD_ROUTING_KEY = "seckill.order.dead";
    public static final String SECKILL_ORDER_DEAD_QUEUE = "queue.seckill.order.dead";

    @Bean
    public DirectExchange seckillOrderExchange() {
        return new DirectExchange(SECKILL_ORDER_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange seckillOrderRetryExchange() {
        return new DirectExchange(SECKILL_ORDER_RETRY_EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange seckillOrderDeadLetterExchange() {
        return new DirectExchange(SECKILL_ORDER_DLX, true, false);
    }

    @Bean
    public Queue seckillOrderQueue() {
        return new Queue(SECKILL_ORDER_QUEUE, true, false, false);
    }

    @Bean
    public Binding seckillOrderBinding(Queue seckillOrderQueue, DirectExchange seckillOrderExchange) {
        return BindingBuilder.bind(seckillOrderQueue)
                .to(seckillOrderExchange)
                .with(SECKILL_ORDER_ROUTING_KEY);
    }

    @Bean
    public Queue seckillOrderRetry5sQueue() {
        return retryQueue(SECKILL_ORDER_RETRY_5S_QUEUE, 5000);
    }

    @Bean
    public Queue seckillOrderRetry30sQueue() {
        return retryQueue(SECKILL_ORDER_RETRY_30S_QUEUE, 30000);
    }

    private Queue retryQueue(String name, int ttl) {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("x-message-ttl", ttl);
        arguments.put("x-dead-letter-exchange", SECKILL_ORDER_EXCHANGE);
        arguments.put("x-dead-letter-routing-key", SECKILL_ORDER_ROUTING_KEY);
        return new Queue(name, true, false, false, arguments);
    }

    @Bean
    public Binding seckillOrderRetryBinding(Queue seckillOrderRetry5sQueue,
                                            DirectExchange seckillOrderRetryExchange) {
        return BindingBuilder.bind(seckillOrderRetry5sQueue)
                .to(seckillOrderRetryExchange)
                .with(SECKILL_ORDER_RETRY_ROUTING_KEY + ".5s");
    }

    @Bean
    public Binding seckillOrderRetry30sBinding(Queue seckillOrderRetry30sQueue,
                                               DirectExchange seckillOrderRetryExchange) {
        return BindingBuilder.bind(seckillOrderRetry30sQueue)
                .to(seckillOrderRetryExchange)
                .with(SECKILL_ORDER_RETRY_ROUTING_KEY + ".30s");
    }

    @Bean
    public Queue seckillOrderDeadQueue() {
        return new Queue(SECKILL_ORDER_DEAD_QUEUE, true, false, false);
    }

    @Bean
    public Binding seckillOrderDeadBinding(Queue seckillOrderDeadQueue,
                                           DirectExchange seckillOrderDeadLetterExchange) {
        return BindingBuilder.bind(seckillOrderDeadQueue)
                .to(seckillOrderDeadLetterExchange)
                .with(SECKILL_ORDER_DEAD_ROUTING_KEY);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(new Jackson2JsonMessageConverter());
        template.setMandatory(true);
        return template;
    }
}
