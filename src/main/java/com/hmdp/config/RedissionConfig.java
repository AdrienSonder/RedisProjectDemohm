package com.hmdp.config;


import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class RedissionConfig {

  @Bean
  public RedissionClient redissionClient(){
    //配置
    Config  config = new Config();
    config.useSingleServer().setAddress("redis://192.168.150.101:6379").setPassword("123456");
    //创建RedissionClient对象
    return Redission.create(config);
  }

}
