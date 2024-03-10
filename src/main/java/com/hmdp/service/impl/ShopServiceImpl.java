package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;

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

  @Autowired
  private StringRedisTemplate stringRedisTemplate;


  private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

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

  @Override
  public void saveShop2Redis(Long id, Long expirSeconds) {
    Shop shop  = getById(id);
    RedisData redisData = new RedisData();
    redisData.setData(shop);
    redisData.setExpireTime(LocalDateTime.now().plusSeconds(expirSeconds));
    stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));
  }

  @Override
  public Shop queryWithLogicalExpire(Long id) {
    String json = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
    if(StrUtil.isBlank(json)){
      return null;
    }
    //命中，将json反序列化为对象
    RedisData redisData = JSONUtil.toBean(json,RedisData.class);
    //将data对象转换为Shop对象
    JSONObject shopJson = (JSONObject) redisData.getData();
    Shop shop = JSONUtil.toBean(shopJson,Shop.class);
    //获取过期时间
    LocalDateTime expireTime = redisData.getExpireTime();
    //判断是否过期
    if(LocalDateTime.now().isBefore(expireTime)){
      //未过期、直接返回商铺信息
      return  shop;
    }
    //过期、尝试获取互斥锁
    boolean flag = tryLock(LOCK_SHOP_KEY + id);
    //获取到锁
    if(flag){
      //开启独立线程
      CACHE_REBUILD_EXECUTOR.submit(()->{
        try{
          this.saveShop2Redis(id,LOCK_SHOP_TTL);
        } catch (Exception e){
          throw new RuntimeException(e);
        } finally{
          unlock(LOCK_SHOP_KEY + id);
        }
      });
      //返回商铺信息
      return shop;
    }
    //未获取到锁
    return shop;
  }

  @Override
  public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
    //1. 判断是否需要根据距离查询
    if (x == null || y == null) {
      // 根据类型分页查询
      Page<Shop> page = query()
              .eq("type_id", typeId)
              .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
      // 返回数据
      return Result.ok(page.getClass());
    }
    //2. 计算分页查询参数
    int from = (current - 1) * SystemConstants.MAX_PAGE_SIZE;
    int end = current * SystemConstants.MAX_PAGE_SIZE;
    String key = SHOP_GEO_KEY + typeId;
    //3. 查询redis、按照距离排序、分页; 结果：shopId、distance
    //GEOSEARCH key FROMLONLAT x y BYRADIUS 5000 m WITHDIST
    GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(key,
            GeoReference.fromCoordinate(x, y),
            new Distance(5000),
            RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));
    if (results == null) {
      return Result.ok(Collections.emptyList());
    }
    //4. 解析出id
    List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
    if (list.size() < from) {
      //起始查询位置大于数据总量，则说明没数据了，返回空集合
      return Result.ok(Collections.emptyList());
    }
    ArrayList<Long> ids = new ArrayList<>(list.size());
    HashMap<String, Distance> distanceMap = new HashMap<>(list.size());
    list.stream().skip(from).forEach(result -> {
      String shopIdStr = result.getContent().getName();
      ids.add(Long.valueOf(shopIdStr));
      Distance distance = result.getDistance();
      distanceMap.put(shopIdStr, distance);
    });
    //5. 根据id查询shop
    String idsStr = StrUtil.join(",", ids);
    List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD( id," + idsStr + ")").list();
    for (Shop shop : shops) {
      //设置shop的举例属性，从distanceMap中根据shopId查询
      shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
    }
    //6. 返回
    return Result.ok(shops);
  }


  private boolean tryLock(String key){
    Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
    return BooleanUtil.isTrue(flag);
  }

  private void unlock(String key){
    stringRedisTemplate.delete(key);
  }


}
