package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private RedisIdWorker redisIdWorker;

    private ExecutorService es = Executors.newFixedThreadPool(500);
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IShopService shopService;

    @Test
    void testWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nexId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }

    @Test
    public void testHyperLogLog() {
        String[] values = new String[1000];
        int j =0;
        for (int i = 0; i < 1000000; i++) {
            j = i % 1000;
            values[j] = "user_" + i;
            if (j == 999) {
                // 添加数据
                stringRedisTemplate.opsForHyperLogLog().add("hl1", values);
            }
        }
        // 统计数量
        Long count = stringRedisTemplate.opsForHyperLogLog().size("hl1");
        System.out.println("count = " + count); //997593
        System.out.println("rate = "+ count/1000000.0);// 0.997593
    }

    @Test
    void loadShopData() {
        //1. 查询店铺信息
        List<Shop> shops = shopService.list();
        //2. 店铺信息分组，按typeId分组
        Map<Long, List<Shop>> map = shops.stream().collect(Collectors.groupingBy(Shop::getTypeId)); // 分组
        //3. 批量插入数据到redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            String key = RedisConstants.SHOP_GEO_KEY + typeId;
            //3.2 获取分组数据
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>();
            //3.3 转换为redis GEO数据结构
            for (Shop shop : value){
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            //3.1 写入redis GEOADD key 经度 纬度 member
            stringRedisTemplate.opsForGeo().add(key,locations);
        }
    }
}
