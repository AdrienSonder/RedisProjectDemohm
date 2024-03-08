package com.hmdp.Interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.jws.soap.SOAPBinding.Use;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

public class LoginInterceptor implements HandlerInterceptor {

  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
   /* //获取Session
    HttpSession session = request.getSession();
    //获取Session中的信息
    // User user = (User) session.getAttribute("user");
    UserDTO user = (UserDTO) session.getAttribute("user");
    //判断用户是否存在
    if (user == null) {
      //不存在，拦截
      response.setStatus(401);
      return false;
    }
    //存在，保存用户信息到ThreadLocal,UserHolder是提供好了的工具类
    UserHolder.saveUser(user);
    return true;*/
  /*  //1.获取请求头中的token
    String token = request.getHeader("authorization");
    //如果token是空，则未登录，拦截
    if(StrUtil.isBlank(token)){
      response.setStatus(401);
      return false;
    }
    String key = RedisConstants.LOGIN_USER_KEY + token;
    //基于token获取redis中的用户数据
    Map<Object,Object> userMap = stringRedisTemplate.opsForHash().entries(key);
    //判断用户是否存在，不存在，则拦截
    if(userMap.isEmpty()){
      response.setStatus(401);
      return false;
    }
    //将查询到的Hash数据转化为UserDto对象
    UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap,new UserDTO(),false);
    //将用户信息保存到ThreadLocal中
    UserHolder.saveUser(userDTO);
    //刷新TokenTTL,存活时间设定为三十分钟
    stringRedisTemplate.expire(key,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
    return true;*/
    if(UserHolder.getUser() == null){
      response.setStatus(401);
      return  false;
    }
    return true;
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
      Object handler, Exception ex) throws Exception {
    UserHolder.removeUser();
  }
}
