package com.hmdp.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RedisIdWorker {

  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  //设置起始时间
  public static  final Long BEGIN_TIMESTAMP = 1640995200L;
  //序列号长度
  public static  final Long COUNT_BIT = 32L;

  public  long nextId (String keyPrefix){
    //生成时间戳
    LocalDateTime now = LocalDateTime.now();
    long currentSecond = now.toEpochSecond(ZoneOffset.UTC);
    long timeStamp = currentSecond - BEGIN_TIMESTAMP;
    //生成序列号
    String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
    long count = stringRedisTemplate.opsForValue().increment("inc:" + keyPrefix + ":" +date);
    return timeStamp << COUNT_BIT | count;
  }
}
