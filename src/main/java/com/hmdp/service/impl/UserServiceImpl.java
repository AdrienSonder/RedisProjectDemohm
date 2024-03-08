package com.hmdp.service.impl;

import static com.hmdp.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_CODE_TTL;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.extra.mail.Mail;
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
import java.nio.file.CopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.servlet.http.HttpSession;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

  @Autowired
  private StringRedisTemplate stringRedisTemplate;


  @Override
  public Result sendCode(String phone, HttpSession session) {
    if(!RegexUtils.isPhoneInvalid(phone)){
      return Result.fail("手机号格式错误");
    }
    String code = MailUtils.achieveCode();
    stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);
    //log.info("验证码:{}",code);
    return Result.ok();
  }

  /**
   *  1. 验证手机号
   *  2. 验证验证码
   *  3. 判断用户是否已经存在
   *  4. 存在则加入
   * @param loginForm
   * @param session
   * @return
   */
  @Override
  public Result login(LoginFormDTO loginForm, HttpSession session) {
    String phone = loginForm.getPhone();
    String userCode = loginForm.getCode();
    //获取redis中的验证码
    String sessionCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
    if(userCode == null || !userCode.equals(sessionCode)){
      //log.info("用户输入:{},应为:{}",userCode,sessionCode);
      return Result.fail("验证码错误");
    }
    LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(phone != null,User::getPhone,phone);
    User user = this.getOne(queryWrapper);
    if(user == null){
      user = createUserWithPhone(phone);
    }
    //保存用户信息到session中
   //生成随机token,作为登录令牌
    String token = UUID.randomUUID().toString();
    //将UserDto对象转换为HashMap存储
    UserDTO userDTO = BeanUtil.copyProperties(user,UserDTO.class);
    HashMap<String,String> userMap = new HashMap<>();
    userMap.put("icon",userDTO.getIcon());
    userMap.put("id",String.valueOf(userDTO.getId()));
    userMap.put("nickName",userDTO.getNickName());
    //存储
    String tokenKey = LOGIN_USER_KEY + token;
    stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
    //设置token有效期为30分钟
    stringRedisTemplate.expire(tokenKey,30,TimeUnit.MINUTES);
    //登录成功删除验证码信息
    stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);
    //返回token
    return Result.ok(token);
  }

  private User createUserWithPhone(String phone){
    User user = new User();
    user.setPhone(phone);
    user.setNickName("user_" + RandomUtil.randomString(8));
    this.save(user);
    return user;
  }

}
