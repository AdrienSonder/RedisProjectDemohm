package com.hmdp.fliter;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {

 //   @Autowired
//   private   StringRedisTemplate stringRedisTemplate;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
     /*   //1. 获取session
        HttpSession session = request.getSession();
        //2.获取session中的用户
       // Object user = session.getAttribute("user");
        UserDTO user = (UserDTO) session.getAttribute("user");
        //3. 判断用户是否存在
        if (user == null){
            //4. 不存在，拦截
            response.setStatus(401);
            return false;
        }

        //5. 存在 保存用户信息到ThreadLocal
        UserHolder.saveUser((UserDTO) user);
        //6. 放行
        return true;*/
      //1.获取请求头中的token
      /*  String token = request.getHeader("authorization");
        //2.如果token是空，则未登录，拦截
        if(StrUtil.isBlank(token)){
            response.setStatus(401);
            return false;
        }
        String key = RedisConstants.LOGIN_USER_KEY + token;
        //3.基于token获取Redis中的用户数据
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);
        //4. 判断用户是否存在，不存在则拦截
        if(userMap.isEmpty()){
            response.setStatus(401);
            return false;
        }
        //5.将查询到的Hash数据转化为UserDto对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap,new UserDTO(),false);
        //6.将用户信息保存到ThreadLocal中
        UserHolder.saveUser(userDTO);
        //7.刷新tokenTTL,存活时间根据自己需求设置
        stringRedisTemplate.expire(key,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;*/
      //判断用户是否存在
      //1. 获取session
      HttpSession session = request.getSession();
      //2.获取session中的用户
      Object user = session.getAttribute("user");

      if (UserHolder.getUser() == null) {
        //不存在则拦截
        response.setStatus(401);
        return false;
      }
      //存在则放行
      return true;
    }
      @Override
      public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.removeUser();
      }
 }

