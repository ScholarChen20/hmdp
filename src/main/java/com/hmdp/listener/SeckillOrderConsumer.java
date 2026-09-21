package com.hmdp.listener;

import com.hmdp.config.RabbitMQConfig;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderConsumer {

    private final IVoucherOrderService voucherOrderService;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = RabbitMQConfig.SECKILL_ORDER_QUEUE)
    public void processOrder(VoucherOrder voucherOrder, Message message) {
        try {
            voucherOrderService.handleVoucherOrderByMq(voucherOrder);
        } catch (Exception e) {
            int retryCount = getRetryCount(message);
            log.error("秒杀订单消费失败，orderId={}, userId={}, voucherId={}, retryCount={}",
                    voucherOrder.getId(), voucherOrder.getUserId(), voucherOrder.getVoucherId(), retryCount, e);
            if (retryCount < 2) {
                rabbitTemplate.convertAndSend(RabbitMQConfig.SECKILL_ORDER_RETRY_EXCHANGE,
                        retryCount == 0
                                ? RabbitMQConfig.SECKILL_ORDER_RETRY_ROUTING_KEY + ".5s"
                                : RabbitMQConfig.SECKILL_ORDER_RETRY_ROUTING_KEY + ".30s",
                        voucherOrder, outgoing -> {
                            outgoing.getMessageProperties().setHeader("x-retry-count", retryCount + 1);
                            outgoing.getMessageProperties().setDeliveryMode(
                                    org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
                            return outgoing;
                        });
            } else {
                rabbitTemplate.convertAndSend(RabbitMQConfig.SECKILL_ORDER_DLX,
                        RabbitMQConfig.SECKILL_ORDER_DEAD_ROUTING_KEY, voucherOrder, outgoing -> {
                            outgoing.getMessageProperties().setHeader("x-failure-reason",
                                    e.getClass().getSimpleName() + ": " + e.getMessage());
                            outgoing.getMessageProperties().setDeliveryMode(
                                    org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
                            return outgoing;
                        });
            }
            throw e;
        }
    }

    private int getRetryCount(Message message) {
        Object value = message.getMessageProperties().getHeaders().get("x-retry-count");
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof byte[]) {
            return Integer.parseInt(new String((byte[]) value, StandardCharsets.UTF_8));
        }
        return value == null ? 0 : Integer.parseInt(value.toString());
    }
}
