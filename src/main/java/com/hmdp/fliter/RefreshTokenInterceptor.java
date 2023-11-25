package com.hmdp.fliter;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

public class RefreshTokenInterceptor implements HandlerInterceptor {

  @Resource
  private StringRedisTemplate stringRedisTemplate;

  public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate){
    this.stringRedisTemplate = stringRedisTemplate;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    //1.获取请求头中的token
    String token = request.getHeader("authorization");
    //2.如果token是空，直接放行，交给LoginInterceptor处理
    if(StrUtil.isBlank(token)){
      return  true;
    }
    String key = RedisConstants.LOGIN_USER_KEY + token;
    //3.基于token获取Redis中的用户数据
    Map<Object,Object> userMap = stringRedisTemplate.opsForHash().entries(key);
    //4.判断用户是否存在，不存在，也放行，交给LoginInterceptor
    if(userMap.isEmpty()){
      return true;
    }
    //5.将查询到的Hash数据转化为UserDto对象
    UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap,new UserDTO(),false);
    //6.将用户信息保存到ThreadLocal
    UserHolder.saveUser(userDTO);
    //7.刷新tokenTTL,存活时间自行设置
    stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
    return true;
  //  return HandlerInterceptor.super.preHandle(request, response, handler);
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
      Object handler, Exception ex) throws Exception {
    UserHolder.removeUser();
  }
}
