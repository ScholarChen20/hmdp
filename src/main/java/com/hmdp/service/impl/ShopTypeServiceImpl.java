package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        String key = RedisConstants.CACHE_SHOP_LIST_KEY;
        // 1.查询缓存中的类型数据,转为List<ShopType>
        List<String> type = stringRedisTemplate.opsForList().range(key, 0, -1); // 返回指定范围的元素，包含头尾
        //2. 如果存在，直接返回
        if (type != null && type.size() > 0) {
            List<ShopType> shopTypes = type.stream().map(s -> JSONUtil.toBean(s, ShopType.class)).collect(Collectors.toList()); // 将json转为对象，转为对象列表
            return Result.ok(shopTypes);
        }
        //3. 如果不存在，查询数据库，并缓存到redis
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        //3.1 如果查询失败，返回失败信息
        if (shopTypes == null) {
            return Result.fail("查询失败");
        }
        //3.2 查询数据库,并加入redis缓存
        stringRedisTemplate.opsForList().rightPushAll(key, shopTypes.stream().map(JSONUtil::toJsonStr).collect(Collectors.toList())); // 使用右推确保顺序与查询一致        //4. 返回查询结果
        return Result.ok();
    }
}
