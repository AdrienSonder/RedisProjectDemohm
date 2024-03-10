package com.hmdp.service.impl;

import com.hmdp.service.ILock;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;

public class SimpleRedisLock implements ILock {

  //锁的前缀
  private static final String KEY_PREFIX = "lock:";
  //具体业务名称
  private String name;
  //构造器注入
  private StringRedisTemplate stringRedisTemplate;

  public  SimpleRedisLock(String name,StringRedisTemplate stringRedisTemplate){
    this.name = name;
    this.stringRedisTemplate = stringRedisTemplate;
  }

  /**
   * 尝试获取锁
   *
   * @param timeoutSec 锁持有的超时时间，过期自动释放
   * @return true表示获取锁成功，false表示获取锁失败
   */
  @Override
  public boolean tryLock(long timeoutSec) {
    //获取线程标识
    long threadId = Thread.currentThread().getId();
    //获取锁
    Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name, threadId + "",timeoutSec,
        TimeUnit.SECONDS);
    //防止自动拆箱出现null
    return Boolean.TRUE.equals(success);
  }

  /**
   * 释放锁
   */
  @Override
  public void unlock() {
    stringRedisTemplate.delete(KEY_PREFIX + name);
  }
}
