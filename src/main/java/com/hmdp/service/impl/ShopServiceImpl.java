package com.hmdp.service.impl;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
    //从redis中查，常量值为固定的前缀 + 店铺id
    String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
    //如果不为空，则转为shop类型直接返回
    if(StrUtil.isNotBlank(shopJson)){
      Shop shop = JSONUtil.toBean(shopJson,Shop.class);
      return Result.ok(shop);
    }
    //否则去数据库中查询
    Shop shop  = getById(id);
    //查询不到返回一个错误信息或者空都可以
    if(shop == null){
      return Result.fail("店铺不存在!");
    }
    //查到了转为json字符串
    String jsonStr = JSONUtil.toJsonStr(shop);
    //存入redis中
    stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id,jsonStr);
    //最终将查询到的商品信息返回给前端
    return Result.ok(shop);
  }
}
