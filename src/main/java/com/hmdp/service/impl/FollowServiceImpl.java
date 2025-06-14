package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
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
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private IUserService  userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result isFollow(Long followUserId) {
        //1.查询是否关注
        Long userId = UserHolder.getUser().getId();
        //2.查询是否关注 select * from follow where user_id = ? and follow_user_id = ?
        int count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count(); //eq 查询条件
        //3.返回结果
        return Result.ok(count > 0);
    }

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //获取登录用户
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        //1. 判断是否已经关注
        if(isFollow){
            //2. 关注
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuceess = save(follow);
            if(isSuceess){
                //保存成功，添加互关

                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        }else{
            //3. 取消关注 delete from follow where user_id = ? and follow_user_id = ?
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId));
            if(isSuccess){
                //删除成功，删除互关
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result followCommons(Long id) {
        //1. 获取当前用户
        Long userId = UserHolder.getUser().getId();
        //2. 查询互关列表
        String key = "follows:" + userId;
        String key2 = "follows:" + id;
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if(intersect == null || intersect.isEmpty()){
            return Result.ok(Collections.emptyList()); //返回空列表
        }
        //3. 转为id列表
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //4. 查询用户
        List<UserDTO> users = userService.listByIds(ids)
                .stream() //转为流
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))   //转为UserDTO
                .collect(Collectors.toList()); //转为列表
        return Result.ok(users);
    }
}
