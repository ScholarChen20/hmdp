package com.hmdp.listener;

import com.hmdp.config.RabbitMQConfig;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SeckillOrderConsumer {

    private final IVoucherOrderService voucherOrderService;

    @RabbitListener(queues = RabbitMQConfig.SECKILL_ORDER_QUEUE) // 监听队列
    public void processOrder(VoucherOrder voucherOrder) {
        try {
            voucherOrderService.handleVoucherOrderByMq(voucherOrder); // 处理订单
        } catch (Exception e) {
            // 可以记录日志后重试或者拒绝消息
            System.err.println("处理订单失败：" + e.getMessage());
        }
    }
}
