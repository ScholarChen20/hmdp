package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.config.RabbitMQConfig;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.connection.stream.Consumer;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient  redissonClient;
    @Resource
    private RabbitTemplate rabbitTemplate;


    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor(); //  创建单个线程池，用于处理订单队列

    /**
     * 线程池初始化，启动订单处理线程
     * todo 使用rabbitmq时需要注释掉
     */
    @PostConstruct  /// 启动时执行该方法
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler()); // 启动线程后启动线程处理订单队列
    }

    /**
     * todo 使用rabbitmq时需要注释掉
     * 处理订单队列.Redis提供三种不同的方式实现消息队列
     * 1. List结构：List结构是Redis中最简单的消息队列实现方式，可以实现简单的消息队列功能。
     * 2. Stream结构：Stream结构是Redis中一种更高级的消息队列实现方式，可以实现更复杂的消息队列功能。
     * 3. Pub/Sub结构：Pub/Sub结构是Redis中一种发布/订阅模式的消息队列实现方式，可以实现发布/订阅模式的消息队列功能。
     */
    private class VoucherOrderHandler implements Runnable{
        String queueName = "stream.orders";
        @Override
        public void run() {
            while( true){
                try {
                    // 获取消息队列的信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );


                    //2.判断消息获取是否成功
                    if(list == null || list.isEmpty()){
                        continue;
                    }
                    //3. 获取失败，继续下一次循环
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder  = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    //4. 获取成功，解析消息
                    handleVoucherOrder(voucherOrder);
                    //5. ACK确认 stream.orders g1 c1
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1",record.getId()); //确认消息
                } catch (Exception e) {
                    log.info("处理订单异常",e);
                    handlePendingList();
                }
            }
        }

        /**
         * 处理延时队列中的订单，获取pending-list中的订单，处理订单。
         * Redis Stream需要自己维护延时队列，手动记录失败消息。需要手动实现ACK确认和消费确认 。
         * todo 使用rabbitmq时需要注释掉
         */
        private void handlePendingList() {
            while (true){
                try {
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create(queueName, ReadOffset.from("0"))
                    );
                    //2.判断消息获取是否成功
                    if(list == null || list.isEmpty()){
                        // 如果获取失败，说明pending-list没有消息，结束循环
                        break;
                    }
                    //3. 获取失败，继续下 once
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder  = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    //4. 获取成功，解析消息, 可以下单
                    handleVoucherOrder(voucherOrder);
                    //5. ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1",record.getId()); //确认消息
                }catch (Exception e){
                    log.info("处理pending-list订单异常",e);
                    try  {
                        Thread.sleep(2000);
                    } catch (InterruptedException  interruptedException) {
                        interruptedException.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * 处理订单，使用Redisson分布式锁实现线程安全，可重试
     * todo 使用rabbitmq时需要注释掉
     * @param voucherOrder
     */
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        // 创建Redisson分布式锁对象，该锁保证线程安全，可重试
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 获取锁
        boolean isLock = lock.tryLock();
        // 判断获取锁是否成功
        if(!isLock){
            log.info("不允许重复下单");
            return;
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
            //事务提交之后即可释放锁
        } finally {
            lock.unlock(); //释放锁
        }
    }

    /**
     * 处理订单，使用Redisson分布式锁实现线程安全, 使用MQ实现消息队列，可重试
     * 秒杀高峰期时，RabbitMQ 可轻松横向扩展多个消费者节点来提升订单处理能力，而 Redis Stream 需要额外开发才能实现类似效果。
     * RabbitMQ 可通过设置最大重试次数后将失败消息转发到死信队列，便于后续人工干预或补偿处理，减少订单丢失风险。
     * @param voucherOrder
     */
    public void handleVoucherOrderByMq(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        // 创建Redisson分布式锁对象，该锁保证线程安全，可重试
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 获取锁
        boolean isLock = lock.tryLock();
        // 判断获取锁是否成功
        if(!isLock){
            log.info("不允许重复下单");
            return;
        }
        try {
            proxy.createVoucherOrder(voucherOrder);
            //事务提交之后即可释放锁
        } finally {
            lock.unlock(); //释放锁
        }
    }

    /**
     * 秒杀优惠券LUA脚本初始化，加载脚本内容
     */
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }
    private IVoucherOrderService proxy;

    /**
     * 秒杀优惠券, 使用LUA脚本实现秒杀下单。
     * 基于lua脚本实现库存预扣与并发校验，保证秒杀下单的原子性。
     * @param voucherId
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        // 获取用户
        Long userId = UserHolder.getUser().getId();
        Long orderId = redisIdWorker.nexId("order");
        //1. 执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(),String.valueOf(orderId)
        );
        //2.判断结果是否为0
        int r = result.intValue();
        //2.1 不为0没有购买资格
        if(r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        //发送消息到rabbitmq队列中
        rabbitTemplate.convertAndSend(RabbitMQConfig.SECKILL_ORDER_QUEUE, voucherOrder);

        //2.2 购买资格，把下单信息保存到阻塞队列
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderId);
    }

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024); /// 创建阻塞队列，消耗JVM内存, 存在数据不一致问题
    /**
     * 基于阻塞队列实现秒杀下单，存在内存限制问题和数据安全问题，存在数据不一致问题。
     * 阻塞队列占用JVM内存，存在数据不一致问题。
     */
    @Override
    public Result seckillVoucherByQueue(Long voucherId) {
        //获取用户
        Long userId = UserHolder.getUser().getId();
        //1. 执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );
        //2.判断结果是否为0
        int r = result.intValue();
        //2.1 不为0没有购买资格
        if(r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //2.2 购买资格，把下单信息保存到阻塞队列
        long orderId = redisIdWorker.nexId("order");
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        //创建阻塞队列
        orderTasks.add(voucherOrder);
        //也可使用rabbitmq 实现异步处理
        //3. 获取代理带向
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        return Result.ok(orderId);
    }

//    /**
//     * 秒杀优惠券基于Redis分布式锁锁实现，无法保证线程安全，不可重试
//     * @param voucherId
//     * @return
//     */
//    @Override
//    public Result seckillVoucherByRedisLock(Long voucherId) {
//        //1.查询优惠券信息
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//        //2. 判断秒杀是否开始
//        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
//            return Result.fail("秒杀尚未开始");
//        }
//        //3. 判断秒杀是否结束
//        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
//            return Result.fail("秒杀已结束");
//        }
//        //4.判断库存是否充足，是，扣减库存
//        if(voucher.getStock() < 1){
//            return Result.fail("库存不足");
//        }
//        Long userId = UserHolder.getUser().getId();
//        // 创建锁对象
////        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);  //redisson锁
//        // 获取锁
//        boolean isLock = lock.tryLock();
//        // 判断获取锁是否成功
//        if(!isLock){
//            return Result.fail("不允许重复下单");
//        }
//        try {
//            //事务代理对象
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucher);
//            //事务提交之后即可释放锁
//        } finally {
//            lock.unlock(); //释放锁
//        }
//    }

    /**
     * 创建订单，使用乐观锁实现防止超卖，可重试
     * @param voucherOrder
     * @return
     */
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //1. 一人一单
        Long userId = voucherOrder.getUserId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
        if(count > 0){
            log.info("不允许重复下单");
            return;
        }

        //2.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherOrder.getVoucherId()) //eq表示等于
                .gt("stock", 0) //where id =? and  stock = ?
                .update();
        if(!success){
            log.info("库存不足");
            return;
        }
        //3. 创建订单
        save(voucherOrder);
    }

}
