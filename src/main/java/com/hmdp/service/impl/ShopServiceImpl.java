package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import java.sql.Time;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

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

  private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  @Resource
  private CacheClient cacheClient;

  @Override
  public Result queryById(Long id) {
  /*  //先从Redis中查，这里常量值是固定的前缀 + 店铺 id
    String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
    //如果不为空，则转换为Shop类型直接返回
    if(StrUtil.isNotBlank(shopJson)){
      Shop shop = JSONUtil.toBean(shopJson,Shop.class);
      return Result.ok(shop);
    }
    if(shopJson != null){
      return Result.fail("店铺不重要！！");
    }
    //否则去数据库中查
    Shop shop = getById(id);
    //查不到返回一个错误信息或者返回空都可以
    if(shop == null){
      //常量值设为两分钟
      stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
      return Result.fail("店铺不存在！！！");
    }
    //查到了就转换成json字符串
    String jsonStr = JSONUtil.toJsonStr(shop);
    //并存入Redis 设置TTL
   // stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,jsonStr,);
    stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, jsonStr,CACHE_SHOP_TTL, TimeUnit.MINUTES);
    //最终把查询到的商品信息返回给前端
    return Result.ok(shop);*/
    //Shop shop = queryWithMutex(id);
    Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY,id,Shop.class,this::getById,CACHE_SHOP_TTL,TimeUnit.MINUTES);
    if(shop == null){
      return Result.fail("店铺不存在！！");
    }
    return Result.ok(shop);
  }

  @Override
  public Result update(Shop shop) {
      //首先判空
    if(shop.getId() == null){
      return Result.fail("店铺id不能为空！！");
    }
    //先修改数据库
    updateById(shop);
    //再删除缓存
    stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
    return Result.ok();
  }

  @Override
  public Shop queryWithPassThrough(Long id) {
    //先从Redis查看，常量值为固定的前缀 + 店铺id
    String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
    //如果不为空，则转换为Shop类型直接返回
    if(StrUtil.isNotBlank(shopJson)){
      Shop shop = JSONUtil.toBean(shopJson,Shop.class);
      return shop;
    }
    if(shopJson != null){
      return null;
    }
    //否则去数据库中查询
    Shop shop  = getById(id);
    //查不到。则将空值写入Redis
    if(shop == null){
      stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,"",CACHE_NULL_TTL, TimeUnit.MINUTES);
      return null;
    }
    //查到了则转换为Json字符串
    String jsonStr = JSONUtil.toJsonStr(shop);
    //并存入redis，设置TTL
    stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,jsonStr,CACHE_SHOP_TTL,TimeUnit.MINUTES);
    //并把查询结果的商户返回给前端
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
      return  null;
    }
    Shop shop = null;
    try{
      Boolean flag = tryLock(LOCK_SHOP_KEY + id);
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
      stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY  + id,jsonStr,CACHE_SHOP_TTL,TimeUnit.MINUTES);

    }catch (InterruptedException e){
      throw new RuntimeException(e);
    }finally {
      unlock(LOCK_SHOP_KEY + id);
    }
    return shop;
  }



  private boolean tryLock(String key){
    Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.MINUTES);
    //避免返回值为null,使用BooleanUtil工具类
    return BooleanUtil.isTrue(flag);
  }

  private void unlock(String key){
    stringRedisTemplate.delete(key);
  }


  public void saveShop2Redis(Long id,Long expireSeconds){
    //1.查询店铺数据
    Shop shop = getById(id);
    //2.封装逻辑过期时间
    RedisData redisData = new RedisData();
    redisData.setData(shop);
    redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
    //3.写入redis
    stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
  }

  @Override
  public Shop queryWithLogicalExpire(Long id){
    String key = CACHE_SHOP_KEY + id;
    //1.从Redis查询商铺缓存
    String json = stringRedisTemplate.opsForValue().get(key);
    //2.判断是否存在
    if(StrUtil.isBlank(json)){
      //3.存在，直接返回
      return null;
    }
    //4.命中，需要先把json反序列化为对象
    RedisData redisData = JSONUtil.toBean(json,RedisData.class);
    Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(),Shop.class);
    LocalDateTime expireTime = redisData.getExpireTime();
    //5.判断是否过期
    if(expireTime.isAfter(LocalDateTime.now())){
      //5.1未过期、之恶返回店铺信息
      return shop;
    }
    //5.2已过期，需要缓存重建
    //6.缓存重建
    //6.1获取互斥锁
    String lockKey = LOCK_SHOP_KEY + id;
    boolean isLock = tryLock(lockKey);
    //6.2判断是否获取锁成功
    if(isLock){
      CACHE_REBUILD_EXECUTOR.submit(()->{

        try{
          //重建缓存
          this.saveShop2Redis(id,20L);
        } catch (Exception e){
          throw  new RuntimeException(e);
        } finally {
          unlock(lockKey);
        }
      });
    }
    //6.4返回过期的商铺信息
    return shop;
  }

  @Override
  public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
    //1.判断是否需要根据距离查询
    if(x == null || y == null){
      //根据类型分页查询
      Page<Shop>  page =  query()
              .eq("type_id",typeId)
              .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
      //返回数据
      return Result.ok(page.getRecords());
    }
    //2.计算分页查询参数
    int from =  (current - 1) * SystemConstants.MAX_PAGE_SIZE;
    int end = current * SystemConstants.MAX_PAGE_SIZE;
    String key = SHOP_GEO_KEY + typeId;
    //3.查询redis 、 按照距离查询、分页，结果：ShopId、distance;
   GeoResults<RedisGeoCommands.GeoLocation<String>> results =  stringRedisTemplate.opsForGeo().search(
            key,
            GeoReference.fromCoordinate(x,y),
            new Distance(5000),
            RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));
    if(results == null){
      return Result.ok(Collections.emptyList());
    }
    //4.解析出id
    List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
    if(list.size() < from){
      //起始查询位置大于数据总量，说明没有数据，返回空集合
      return Result.ok(Collections.emptyList());
    }
    ArrayList<Long> ids = new ArrayList<>(list.size());
    HashMap<String,Distance> distanceMap = new HashMap<>(list.size());
    list.stream().skip(from).forEach(result->{
      String shopIdStr = result.getContent().getName();
      ids.add(Long.valueOf(shopIdStr));
      Distance distance = result.getDistance();
      distanceMap.put(shopIdStr,distance);
    });
    //5.根据id查询shop
    String idsStr = StrUtil.join(",",ids);
    List<Shop> shops = query().in("id",ids).last("ORDER BY FIELD(id," + idsStr + ")").list();
    for(Shop shop : shops){
      //设置shop的距离属性，从distanceMap中根据shopId查询
      shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
    }
    //6.返回
    return Result.ok(shops);
  }


}
