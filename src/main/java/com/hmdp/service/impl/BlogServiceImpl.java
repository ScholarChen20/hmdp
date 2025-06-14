package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Resource
    private IFollowService followService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryBlogById(Long id) {
        //1. 查询blog
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在！");
        }
        //2. 查询用户
        queeryBlogUser(blog);
        //3. 查询blog是否被点赞
        isBlogLiked(blog);
        //4. 返回结果
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null) {
            return; //  未登录，无需查询点赞状态
        }
        // 1. 获取当前登录用户id
        Long userId = UserHolder.getUser().getId();
        // 2.判断当前登录用户是否已经点赞
        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score!= null);
    }

    /**
     * 查询用户信息
     * @param blog
     */
    private void queeryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        //3. 设置用户信息
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

    @Override
    public Result queryHotBlog(Integer current) {
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->  {
            this.queeryBlogUser(blog);
            this.isBlogLiked(blog);
        }); // 遍历查询用户信息
        // 返回结果
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        // 获取登录用户
        Long userId = UserHolder.getUser().getId();
        //1. 判断当前用户是否点赞
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            //2. 如果未点赞，可以点赞
            //2.1. 数据库点赞加1
            boolean isSuccess = update()
                    .setSql("liked = liked + 1")
                    .eq("id", id).update();
            //2.2 保存用户到redis的set集合
            if (isSuccess){
                stringRedisTemplate.opsForZSet().add(key, userId.toString(),  System.currentTimeMillis());
            }
        }else{
            //3. 如果已点赞，取消点赞
            boolean isSuccess = update()
                    .setSql("liked = liked - 1")
                    .eq("id", id).update();
            //3.1. 删除用户从redis的set集合
            if (isSuccess){
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        //4. 返回结果
        return Result.ok();
    }

    /**
     * 查询blog点赞用户信息
     * @param id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        // 1. 查询top5的点赞用户 zrange key 0 4
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(top5 == null || top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        // 2. 转换成id列表
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        // 3. 查询用户信息
        List<UserDTO> users = userService.query()
                .in("id",ids).last("ORDER BY FIELD(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        //4. 返回结果
        return Result.ok(users);
    }

    @Override
    public Result saveBlog(Blog blog) {
        //1. 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        //2. 保存blog
        boolean isSuccess = save(blog);
        //3. 返回结果
        if (!isSuccess) {
            return Result.fail("新增笔记失败！");
        }
        //3. 查询作者的所有粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        //4， 推送笔记id给所有粉丝
        for(Follow follow : follows){
            Long userId = follow.getUserId();
            String key = RedisConstants.FEED_KEY  + userId;
           stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //1.查询当前用户
        Long userId = UserHolder.getUser().getId();
        //2. 查询收件箱 ZREVRANGBYSCORE key max min LIMIT offset count
        String key = RedisConstants.FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2); //取出来的数据是带分数的
        if(typedTuples == null || typedTuples.isEmpty()){
            return Result.ok();
        }
        //3. 解析数据：blogId,minTime(时间戳）、offset(偏移量)
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        for(ZSetOperations.TypedTuple<String> typedTuple : typedTuples){
            //3.1 获取id
            ids.add(Long.valueOf(typedTuple.getValue()));
            //3.2 获取时间戳
            long l = typedTuple.getScore().longValue();
            if(minTime == l){
                os++; //  如果时间戳相同，则偏移量加1
            }else{
                minTime = l; //  时间戳不同，更新时间戳
                os = 1;  //  时间戳不同，偏移量重置为1
            }
        }
        //4. 查询blog信息
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids)
                .last("ORDER BY FIELD(id," + idStr + ")").list();
        //5. 查询blog有关的点赞和是否有被点赞
        blogs.forEach(blog ->{
            this.queeryBlogUser(blog);
            this.isBlogLiked(blog);
        });

        //5. 封装并返回
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setMinTime(minTime);
        scrollResult.setOffset(os);
        return Result.ok(scrollResult);
    }
}
