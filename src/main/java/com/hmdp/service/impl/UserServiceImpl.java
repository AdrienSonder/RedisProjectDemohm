package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.MailUtils;
import com.hmdp.utils.RegexUtils;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import static com.hmdp.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_CODE_TTL;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    /*
    修改sendCode方法 逻辑如下: 验证手机号/邮箱格式 不正确则返回错误信息 正确则发送验证码
     */
  //  @Override
    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session)
        throws MessagingException {
      /* 1. 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }

        //3. 符合，生成验证码
        String code = RandomUtil.randomNumbers(6);
        //4. 保存验证码到session
        session.setAttribute("code",code);
        //5. 发送验证码
        log.debug("发送短信验证码成功，验证码:{}",code);
        //返回ok
        return Result.ok();*/
        if(RegexUtils.isEmailInvalid(phone)){
            return Result.fail("邮箱格式不正确");
        }
        String code = MailUtils.achieveCode();
        // session.setAttribute(phone,code);
        // stringRedisTemplate.opsForValue().set("login:code:" + phone, code, 2, TimeUnit.MINUTES);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        log.info("发送登录验证码:{}",code);
       // MailUtils.sendTestMail(phone,code);
        return Result.ok();
    }

  //  @Override
   @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session) {
      /*  //1. 校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        //2. 校验验证码
        Object cacheCode = session.getAttribute("code");
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.toString().equals(code)){
            //3. 不一致，报错
            return Result.fail("验证码错误");
        }

        //4.一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();

        //5. 判断用户是否存在
        if (user == null){
            //6. 不存在，创建新用户
            user = createUserWithPhone(phone);
        }

        //7.保存用户信息到session
        // session.setAttribute("user",BeanUtil.copyProperties(user,UserDTO.class));
       UserDTO userDTO = BeanUtil.copyProperties(user,UserDTO.class);
       session.setAttribute("user",userDTO);
        return Result.ok();*/
        // 实现登录功能
        //return userService.login(loginForm, session);
        //获取登录账号
        String phone = loginForm.getPhone();
        //获取登录验证码
        String userCode =loginForm.getCode();
        //获取session中的验证码
      //  Object cacheCode = session.getAttribute(phone);
       //获取Redis中的验证码
       //String sessionCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + userCode);
       String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);     //1.校验邮箱
        if(RegexUtils.isEmailInvalid(phone)){
            //2.不符合格式报错
            return Result.fail("邮箱格式不正确！！！请重新输入");
        }

        //3.校验验证码
        log.info("code:{},cacheCode{}",userCode,cacheCode);
        if(cacheCode == null || !cacheCode.toString().equals(userCode)){
            //4.不一致则报错
            return Result.fail("验证码不一致！！！");
        }
        //5.根据账户查用用户是否存在
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getPhone,phone);
        User  user = this.getOne(queryWrapper);
        //6.如果不存在则创建
        if(user == null){
            //创建的逻辑封装成一个方法
            user = createUserWithPhone(phone);
        }
        //7.保存用户信息到session中
      //  session.setAttribute("user",user);
       //7.保存用户信息到Redis中
       //7.1随机生成token,作为登录令牌
       String token = UUID.randomUUID().toString();
        //7.2将UserDTO对象转换成HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user,UserDTO.class);
       HashMap<String,String> userMap = new HashMap<>();
       userMap.put("icon",userDTO.getIcon());
       userMap.put("id",String.valueOf(userDTO.getId()));
       userMap.put("nickName",userDTO.getNickName());
       //7.3存储
       String tokenKey = LOGIN_USER_KEY + token;
       stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
       //7.4设置token有效期为30分钟
       stringRedisTemplate.expire(tokenKey,30,TimeUnit.MINUTES);
       // session.setAttribute("user",userDTO);
       // 8. 返回token
        return Result.ok();
    }



    private User createUserWithPhone(String phone) {
        // 1.创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 2.保存用户
        save(user);
        return user;
    }
}
