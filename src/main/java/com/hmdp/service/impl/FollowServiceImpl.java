package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.utils.UserHolder;


public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

  @Override
  public Result follow(Long followUserId, Boolean isFollow) {
    //获取当前用户的id
    Long userId = UserHolder.getUser().getId();
    //判断是否关注
    if (isFollow) {
      //关注，则将信息保存到数据库
      Follow follow = new Follow();
      follow.setUserId(userId);
      follow.setFollowUserId(followUserId);
      save(follow);
    } else {
      //取关，则将数据从数据库移除
      LambdaQueryWrapper<Follow> queryWrapper = new LambdaQueryWrapper<>();
      queryWrapper.eq(Follow::getUserId,userId).eq(Follow::getFollowUserId,followUserId);
      remove(queryWrapper);
    }
    return Result.ok();
  }

  @Override
  public Result isFollow(Long followUserId) {
    //获取当前登录的userId
    Long userId = UserHolder.getUser().getId();
    LambdaQueryWrapper<Follow> queryWrapper = new LambdaQueryWrapper<>();
    //查询当前用户是否关注了该笔记的博主
    queryWrapper.eq(Follow::getFollowUserId,userId).eq(Follow::getFollowUserId,followUserId);
    //只查询一个count
    int count = this.count(queryWrapper);
    return Result.ok(count > 0 );

  }

  @Override
  public Result followCommons(Long id) {
    return null;
  }
}
