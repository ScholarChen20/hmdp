package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;

    /**
     * 根据id查询店铺信息
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        //缓存穿透
//        Shop shop = queryWithLogicalExpire(id);
        Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById,
                RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES); //  缓存穿透
//        Shop shop2 = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES); //缓存击穿
        if(shop == null){
            return Result.fail("店铺不存在！");
        }
        //4. 返回查询结果
        return Result.ok(shop);
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public Shop queryWithLogicalExpire(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // 1. 从redis中查询店铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2. 如果缓存中有数据，则直接返回缓存数据
        if(StrUtil.isBlank(shopJson)){
           return null;
        }
        //3. 命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //4. 判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            //4.1 未过期，直接返回店铺信息
            return  shop;
        }
        //4.2 过期，需要缓存重建
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if(isLock){
            //4.2.1 成功，开启独立线程池，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                //5.1 缓存重建
                try {
                    this.saveShop2Redis(id,RedisConstants.CACHE_SHOP_TTL);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //5.2 解锁
                    unLock(lockKey);
                }
            });
        }
        // 将数据写入redis中
//        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }
    public Shop queryWithMutex(Long id){
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // 1. 从redis中查询店铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2. 如果缓存中有数据，则直接返回缓存数据
        if(StrUtil.isNotBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);// 将json字符串转换为对象
            return shop;
        }
        // 判断命中的是否是空值
        if(shopJson != null){
            return null;
        }
        //3. 实现缓存重建
        //3.1 获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            if(!isLock){        //3.2 判断获取锁是否成功
                //3.3 失败，则休眠从重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            shop = getById(id);
            Thread.sleep(200);  // 模拟重建延迟
            // 判断数据库中是否存在数据
            if(shop == null){
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            // 将数据写入redis中
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //3.4 解锁
            unLock(lockKey);
            return shop;
        }
    }
    public Shop queryWithPassThrough(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // 1. 从redis中查询店铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2. 如果缓存中有数据，则直接返回缓存数据
        if(StrUtil.isNotBlank(shopJson)){
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        // 判断命中的是否是空值
        if(shopJson != null){
            return null;
        }
        Shop shop = getById(id);
        // 判断数据库中是否存在数据
        if(shop == null){
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        // 将数据写入redis中
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }
    /**
     * 枷锁
     * @param key
     * @return
     */
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1",10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     *  解锁
     * @param key
     */
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }
    private void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
        //1. 查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);  // 模拟重建延迟
        //2. 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3. 保存到redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional  //// 事务注解，保证数据一致性
    public Result update(Shop shop) {
        //1. 更新数据库
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        updateById(shop);
        //2. 删除redis缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //1. 判断是否需要根据坐标查询
        if(x == null || y == null){
            // 不需要坐标查询，按数据库查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        //2. 计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;  // 起始位置
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE; // 结束位置
        //3. 查询redis，按照距离排序、分页
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() // 1. 根据geohash查询 GEOSEARCH  byradius x y BYRADIUS 10 WITHDISTANCE
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                );// 2. 获取距离排序结果
        //4. 解析出id
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent(); // 5. 获取所有店铺id
        // 如果list的大小小于from，则返回空列表
        if(list.size() <= from){
            return Result.ok(Collections.emptyList());
        }
        //4.1 截取from-end部分
        List<Long> ids = new ArrayList<>(list.size());
        HashMap<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
                    //4.2 获取店铺id
                    String shopIdStr = result.getContent().getName();
                    ids.add(Long.valueOf(shopIdStr));
                    //4.3 获取距离
                    Distance distance = result.getDistance();
                    distanceMap.put(shopIdStr, distance);
                });
        //5. 根据id查询店铺
        String idStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        //6. 返回数据
        return Result.ok(shops);
    }
}
