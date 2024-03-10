package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

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

    @Autowired
    private IUserService userService;


    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Integer id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("评价不存在或已被删除");
        }
        queryBlogUser(blog);
        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
          /*  Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());*/
            queryBlogUser(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + id;
        Boolean isLiked = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
       /* if(BooleanUtil.isFalse(isLiked)){
            //点赞数 + 1
            boolean success  = update().setSql("liked = liked + 1").eq("id",id).update();
            //将用户加入到set集合中
            if(success){
                stringRedisTemplate.opsForSet().add(key,userId.toString());
            }
         //如果当前用户已经点赞，则取消点赞，将用户从set集合中移除
        } else {
            //点赞数 -1
            boolean success = update().setSql("liked = liked - 1").eq("id",id).update();
            if(success){
                //从set集合中移除
                stringRedisTemplate.opsForSet().remove(key,userId.toString());
            }
        }
        return Result.ok();*/
        //尝试获取score
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        //为null表示集合中没有用户
        if (score == null) {
            //点赞数 + 1
            boolean success = update().setSql("liked = liked + 1").eq("id", id).update();
            //将用户加入到set集合
            if (success) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());

            }
        } else {
            //点赞数 - 1
            stringRedisTemplate.opsForZSet().remove(key, userId.toString());
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Integer id) {
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key,0,4);
        if(top5 == null || top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idsStr = StrUtil.join(",",ids);
        List<UserDTO> userDTOS = userService.query().in("id",ids)
                .last("order by field(id," + idsStr + ")")
                .list().stream()
                .map(user -> BeanUtil.copyProperties(user,UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(userDTOS);
    }


    private void queryBlogUser(Blog blog){
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }



    private void isBlogLiked(Blog blog){
        //获取当前用户信息
        UserDTO userDTO = UserHolder.getUser();
        //Long userId = UserHolder.getUser().getId();
        //当用户未登录时,就不判断了，直接return结束逻辑
        if(userDTO == null){
            return;
        }
        //判断当前用户是否点赞
        String key = BLOG_LIKED_KEY + blog.getId();
        //Boolean isMember = stringRedisTemplate.opsForSet().isMember(key,userId.toString());
        //blog.setIsLike(BooleanUtil.isTrue(isMember));
        Double score = stringRedisTemplate.opsForZSet().score(key,userDTO.getId().toString());
        blog.setIsLike(score != null);
    }
}
