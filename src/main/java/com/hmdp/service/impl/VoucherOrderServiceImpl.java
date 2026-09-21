package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.config.RabbitMQConfig;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    private static final String ORDER_LOCK_PREFIX = "lock:seckill:order:";
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT = script("seckill.lua");
    private static final DefaultRedisScript<Long> CONFIRM_SCRIPT = script("seckill-confirm.lua");
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = script("seckill-release.lua");

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private RabbitTemplate rabbitTemplate;
    @Value("${seckill.reservation.timeout-seconds:300}")
    private long reservationTimeoutSeconds;

    private static DefaultRedisScript<Long> script(String location) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(location));
        script.setResultType(Long.class);
        return script;
    }

    @PostConstruct
    private void configureRabbitCallbacks() {
        rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter());
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (correlationData == null) {
                return;
            }
            if (!ack) {
                log.error("秒杀订单消息未被 Exchange 确认，orderId={}, cause={}",
                        correlationData.getId(), cause);
                try {
                    releaseReservation(Long.valueOf(correlationData.getId()));
                } catch (Exception e) {
                    log.error("Publisher Confirm 失败后的库存回补失败，orderId={}",
                            correlationData.getId(), e);
                }
            }
        });
        rabbitTemplate.setReturnCallback((message, replyCode, replyText, exchange, routingKey) -> {
            log.error("秒杀订单消息无法路由，exchange={}, routingKey={}, replyCode={}, replyText={}",
                    exchange, routingKey, replyCode, replyText);
            Object orderId = message.getMessageProperties().getHeaders().get("orderId");
            if (orderId != null) {
                try {
                    releaseReservation(Long.valueOf(String.valueOf(orderId)));
                } catch (Exception e) {
                    log.error("无法路由消息后的库存回补失败，orderId={}", orderId, e);
                }
            }
        });
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        Long orderId = redisIdWorker.nexId("order");
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(),
                voucherId.toString(), userId.toString(), orderId.toString());
        if (result == null || result != 0L) {
            return Result.fail(result != null && result == 1L ? "库存不足" : "不能重复下单");
        }

        VoucherOrder order = new VoucherOrder()
                .setId(orderId)
                .setUserId(userId)
                .setVoucherId(voucherId);
        CorrelationData correlationData = new CorrelationData(orderId.toString());
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.SECKILL_ORDER_EXCHANGE,
                    RabbitMQConfig.SECKILL_ORDER_ROUTING_KEY, order, message -> {
                        message.getMessageProperties().setDeliveryMode(
                                org.springframework.amqp.core.MessageDeliveryMode.PERSISTENT);
                        message.getMessageProperties().setHeader("x-retry-count", 0);
                        message.getMessageProperties().setHeader("orderId", orderId.toString());
                        return message;
                    }, correlationData);
            org.springframework.amqp.rabbit.connection.CorrelationData.Confirm confirm =
                    correlationData.getFuture().get(3, TimeUnit.SECONDS);
            if (!confirm.isAck()) {
                releaseReservation(orderId, userId, voucherId);
                return Result.fail("订单消息发送失败，请稍后重试");
            }
            return Result.ok(orderId);
        } catch (java.util.concurrent.TimeoutException e) {
            log.warn("秒杀订单 Publisher Confirm 超时，保留预扣记录等待补偿，orderId={}", orderId);
            return Result.fail("订单正在处理中，请稍后查询");
        } catch (Exception e) {
            log.error("秒杀订单消息发送异常，orderId={}", orderId, e);
            return Result.fail("订单消息发送失败，请稍后重试");
        }
    }

    @Override
    public Result seckillVoucherByQueue(Long voucherId) {
        return seckillVoucher(voucherId);
    }

    @Override
    @Transactional
    public void handleVoucherOrderByMq(VoucherOrder voucherOrder) {
        RLock lock = redissonClient.getLock(ORDER_LOCK_PREFIX + voucherOrder.getId());
        boolean locked = false;
        try {
            locked = lock.tryLock(2, 30, TimeUnit.SECONDS);
            if (!locked) {
                throw new IllegalStateException("无法获取订单处理锁");
            }
            createVoucherOrder(voucherOrder);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("获取订单处理锁被中断", e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public boolean createVoucherOrder(VoucherOrder voucherOrder) {
        if (getById(voucherOrder.getId()) != null) {
            confirmReservationAfterCommit(voucherOrder.getId());
            return true;
        }
        int inserted = baseMapper.insertIgnore(voucherOrder);
        if (inserted == 0) {
            releaseReservationAfterCommit(voucherOrder);
            return true;
        }
        boolean stockUpdated = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!stockUpdated) {
            throw new IllegalStateException("数据库库存不足");
        }
        confirmReservationAfterCommit(voucherOrder.getId());
        return true;
    }

    private void confirmReservationAfterCommit(Long orderId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            confirmReservation(orderId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                confirmReservation(orderId);
            }
        });
    }

    private void releaseReservationAfterCommit(VoucherOrder order) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            releaseReservation(order.getId(), order.getUserId(), order.getVoucherId());
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                releaseReservation(order.getId(), order.getUserId(), order.getVoucherId());
            }
        });
    }

    private void confirmReservation(Long orderId) {
        stringRedisTemplate.execute(CONFIRM_SCRIPT,
                Arrays.asList(RedisConstants.SECKILL_RESERVATION_KEY + orderId,
                        RedisConstants.SECKILL_RESERVATION_INDEX_KEY),
                orderId.toString());
    }

    @Override
    public void releaseReservation(Long orderId, Long userId, Long voucherId) {
        releaseReservationInternal(orderId, userId, voucherId);
    }

    public void releaseReservation(Long orderId) {
        Map<Object, Object> reservation = stringRedisTemplate.opsForHash()
                .entries(RedisConstants.SECKILL_RESERVATION_KEY + orderId);
        if (reservation.isEmpty()) {
            return;
        }
        releaseReservationInternal(orderId,
                Long.valueOf(String.valueOf(reservation.get("userId"))),
                Long.valueOf(String.valueOf(reservation.get("voucherId"))));
    }

    private void releaseReservationInternal(Long orderId, Long userId, Long voucherId) {
        stringRedisTemplate.execute(RELEASE_SCRIPT,
                Arrays.asList(RedisConstants.SECKILL_RESERVATION_KEY + orderId,
                        RedisConstants.SECKILL_RESERVATION_INDEX_KEY,
                        RedisConstants.SECKILL_STOCK_KEY + voucherId,
                        "seckill:order:" + voucherId),
                orderId.toString(), userId.toString());
    }

    @Scheduled(fixedDelayString = "${seckill.reservation.compensation-delay-ms:60000}")
    public void compensateExpiredReservations() {
        long cutoff = System.currentTimeMillis() / 1000 - reservationTimeoutSeconds;
        Set<String> orderIds = stringRedisTemplate.opsForZSet().rangeByScore(
                RedisConstants.SECKILL_RESERVATION_INDEX_KEY, 0, cutoff, 0, 100);
        if (orderIds == null) {
            return;
        }
        for (String orderIdText : orderIds) {
            Long orderId = Long.valueOf(orderIdText);
            RLock lock = redissonClient.getLock(ORDER_LOCK_PREFIX + orderId);
            boolean locked = false;
            try {
                locked = lock.tryLock(0, 10, TimeUnit.SECONDS);
                if (!locked) {
                    continue;
                }
                if (getById(orderId) != null) {
                    confirmReservation(orderId);
                } else {
                    releaseReservation(orderId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } finally {
                if (locked && lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
    }
}
