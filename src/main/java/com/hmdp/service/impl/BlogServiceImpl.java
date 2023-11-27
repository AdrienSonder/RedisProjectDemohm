package com.hmdp.service.impl;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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
  private StringRedisTemplate stringRedisTemplate;

  @Resource
  private IFollowService followService;

  @Override
  public Result queryBlogById(Long id) {
    //1.查询Blog
    Blog blog = getById(id);
    if(blog == null){
      return Result.fail("笔记不存在!");
    }
    //2.查询blog相关的用户
    queryBlogUser(blog);
    return Result.ok(blog);
  }

  @Override
  public Result queryHotBlog(Integer current) {
    //根据用户查询
    Page<Blog> page = query()
        .orderByDesc("liked")
        .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
    //获取当前页数据
    List<Blog> records = page.getRecords();
    //查询用户
    records.forEach(blog ->{
      queryBlogUser(blog);
      //追加判断blog是否被当前用户点赞，逻辑封装到isBlogLiked方法中
      isBlogLiked(blog);
    });
    return Result.ok(records);
  }

  public Result queryById(Integer id){
    Blog blog = getById(id);
    if (blog == null){
      return Result.fail("评价不存在或者已被删除");
    }
    queryBlogUser(blog);
    //追加判断Blog是否已经被当前用户点赞，逻辑封装到isBlogLiked中
    isBlogLiked(blog);
    return Result.ok(blog);
  }

  private void isBlogLiked(Blog blog){
  /*  //1.获取当前用户信息
    Long userId = UserHolder.getUser().getId();
    //2.判断当前用户是否点赞
    String key  = BLOG_LIKED_KEY + blog.getId();
    Boolean isMember = stringRedisTemplate.opsForSet().isMember(key,userId.toString());
    //3.如果点赞了，则将isLike设置为true
    blog.setIsLike(BooleanUtil.isTrue(isMember));*/
    //1.获取当前用户信息
    UserDTO userDTO = UserHolder.getUser();
    //当用户未登录的时候，不进行判断、直接return结束逻辑
    if(userDTO == null){
      return;
    }
    //2.判断当前用户是否点赞
    String key = BLOG_LIKED_KEY + blog.getId();
    Double score = stringRedisTemplate.opsForZSet().score(key,userDTO.getId().toString());
    blog.setIsLike(score != null);
  }

  @Override
  public Result likeBlog(Long id) {
  /*  //1.获取当前用户信息
    Long userId =  UserHolder.getUser().getId();
    //2.如果用户未点赞，则点赞数+1，同时将用户加入到set集合中去
    String key = BLOG_LIKED_KEY + id;
    Boolean isLiked = stringRedisTemplate.opsForSet().isMember(key,userId.toString());
    if(BooleanUtil.isFalse(isLiked)){
      //点赞数+1
      boolean success = update().setSql("liked = liked + 1").eq("id",id).update();
      //将用户加入set集合中
      if(success){
        stringRedisTemplate.opsForSet().add(key,userId.toString());
      }
      //如果当前用户已经点赞，则取消点赞，将用户从集合中移除
    } else {
      //点赞数 - 1
      boolean success = update().setSql("liked = liked - 1").eq("id",id).update();
      if (success){
        //从 set集合中移除
        stringRedisTemplate.opsForSet().remove(key,userId.toString());
      }
    }
    return Result.ok();*/
    //1.获取当前用户信息
    Long userId = UserHolder.getUser().getId();
    //2.如果当前用户未曾点赞，则点赞数 + 1 ，同时将用户加入set集合中
    String key = BLOG_LIKED_KEY + id;
    //尝试获取score
    Double score = stringRedisTemplate.opsForZSet().score(key,userId.toString());
    //为null,则表示集合中没有该用户
    if(score == null){
      //点赞数 + 1
      boolean success = update().setSql("liked = liked + 1 ").eq("id",id).update();
      //将用户加入到集合中
      if (success){
        stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
      }
      //3.如果当前用户已经点赞，则取消点赞，将用户从set集合中移除
    } else {
      //点赞数  - 1
      boolean success = update().setSql("liked = liked - 1").eq("id",id).update();
      if(success){
        //从set集合移除
        stringRedisTemplate.opsForZSet().remove(key,userId.toString());
      }
    }
    return Result.ok();
  }

  @Override
  public Result queryBlogLikes(Long id) {
//    //1.获取登录用户
//    Long userId = UserHolder.getUser().getId();
//    //判断当前登录用户是否已经点过赞
//    String key = BLOG_LIKED_KEY  + id;
//    Boolean isMember = stringRedisTemplate.opsForSet().isMember(key,userId.toString());
//    if(BooleanUtil.isFalse(isMember)){
//      //3.如果未点赞，可以点赞
//      //3.1数据库点赞数+1
//      boolean isSuccess = update().setSql("liked = liked + 1").eq("id",id).update();
//      //3.2保存用户到Redis的Set集合
//      if(isSuccess){
//        stringRedisTemplate.opsForSet().add(key,userId.toString());
//      } else{
//        //4.如果已经点赞，取消点赞
//        //4.1数据库点赞数-1
//        boolean isSuccess = update().setSql("liked = liked  -1 ").eq("id",id).update();
//        //4.2
//        if(isSuccess){
//          stringRedisTemplate.opsForSet().remove(key,userId.toString());
//        }
//      }
//    }
//    return null;
      String key = BLOG_LIKED_KEY + id;
      //zrange key 0 4 查询到zset中前5个元素
      Set<String> top5 = stringRedisTemplate.opsForZSet().range(key,0,4);
      //如果为空，直接返回一个空集合
      if(top5 == null || top5.isEmpty()){
        return Result.ok(Collections.emptyList());
      }
      List<Long> ids =top5.stream().map(Long::valueOf).collect(Collectors.toList());
      String idsStr = StrUtil.join(",",ids);
      List<UserDTO> userDTOS = userService.query().in("id",ids)
          .last("order by field(id," + idsStr +")")
          .list().stream()
          .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
          .collect(Collectors.toList());
      return Result.ok(userDTOS);
  }

  @Override
  public Result saveBlog(Blog blog) {
    return null;
  }

  @Override
  public Result queryBlogOfFollow(Long max, Integer offset) {
    return null;
  }


  private void queryBlogUser(Blog blog){
    Long userId = blog.getUserId();
    User user =userService.getById(userId);
    blog.setName(user.getNickName());
    blog.setIcon(user.getIcon());
  }

  public Result likeBlo
}







