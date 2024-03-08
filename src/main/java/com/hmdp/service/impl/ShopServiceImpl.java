package com.hmdp.service.impl;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
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
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  @Override
  public Result queryById(Long id) {
 /*   //从redis中查，常量值为固定的前缀 + 店铺id
    String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
    //如果不为空，则转为shop类型直接返回
    if(StrUtil.isNotBlank(shopJson)){
      Shop shop = JSONUtil.toBean(shopJson,Shop.class);
      return Result.ok(shop);
    }
    if(shopJson != null){
      return Result.fail("店铺不存在!");
    }
    //否则去数据库中查询
    Shop shop  = getById(id);
    //查询不到返回一个错误信息或者空都可以  写入空字符串
    if(shop == null){
      stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
      return Result.fail("店铺不存在!");
    }
    //查到了转为json字符串
    String jsonStr = JSONUtil.toJsonStr(shop);
    //存入redis中
    stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,jsonStr,CACHE_SHOP_TTL, TimeUnit.MINUTES);
    //最终将查询到的商品信息返回给前端
    return Result.ok(shop);*/
    Shop shop = queryWithMutex(id);
    if(shop == null){
      return  Result.fail("店铺不存在!");
    }
    return Result.ok(shop);
  }

  @Override
  public Result update(Shop shop) {
    if(shop.getId() == null){
      return Result.fail("店铺id不能为空");
    }
    //先更新数据库
    updateById(shop);
    //在删除缓存
    stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
    return Result.ok();
  }

  @Override
  public Shop queryWithPassThrough(Long id) {
    String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
    if(StrUtil.isNotBlank(shopJson)){
      Shop shop = JSONUtil.toBean(shopJson,Shop.class);
      return shop;
    }
    if(shopJson != null){
      return null;
    }
    //查数据库
    Shop shop = getById(id);
    //查不到将空值写入Redis中
    if(shop == null){
      stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
      return null;
    }
    String jsonStr = JSONUtil.toJsonStr(shop);
    stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,jsonStr,CACHE_SHOP_TTL,TimeUnit.MINUTES);
    return shop;
  }

  @Override
  public Shop queryWithMutex(Long id) {
    String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
    if(StrUtil.isNotBlank(shopJson)){
      Shop shop = JSONUtil.toBean(shopJson,Shop.class);
      return shop;
    }
    if(shopJson != null){
      return null;
    }
    Shop shop = null;
    try{
      boolean flag = tryLock(LOCK_SHOP_KEY + id);
      if(!flag){
        Thread.sleep(50);
        return queryWithMutex(id);
      }
      shop = getById(id);
      if(shop == null){
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
        return null;
      }
      String jsonStr = JSONUtil.toJsonStr(shop);
      stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,jsonStr,CACHE_SHOP_TTL,TimeUnit.MINUTES);
    }catch (InterruptedException e){
      throw  new RuntimeException(e);
    }finally {
      unlock(LOCK_SHOP_KEY+
          id);
    }
    return shop;
  }


  private boolean tryLock(String key){
    Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
    return BooleanUtil.isTrue(flag);
  }

  private void unlock(String key){
    stringRedisTemplate.delete(key);
  }


}
