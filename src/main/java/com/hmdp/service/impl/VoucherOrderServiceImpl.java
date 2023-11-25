package com.hmdp.service.impl;

/*
import static com.hmdp.utils.SimpleRedisLock.ID_PREFIX;
import static com.hmdp.utils.SimpleRedisLock.KEY_PREFIX;
*/

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Resource;
import net.bytebuddy.build.Plugin.Factory.Simple;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.transaction.annotation.Transactional;

public class VoucherOrderServiceImpl extends
    ServiceImpl<VoucherOrderMapper, VoucherOrder> implements
    IVoucherOrderService {

  @Resource
  private ISeckillVoucherService seckillVoucherService;

  @Resource
  private RedisIdWorker redisIdWorker;

  @Resource
  private StringRedisTemplate stringRedisTemplate;

  @Resource
  private RedissonClient redissonClient;

  private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

  static {
    SECKILL_SCRIPT = new DefaultRedisScript<>();
    SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
    SECKILL_SCRIPT.setResultType(Long.class);
  }

  private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
  static {
    UNLOCK_SCRIPT = new DefaultRedisScript<>();
    UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
    UNLOCK_SCRIPT.setResultType(Long.class);
  }

  private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

/*  public void unlock(){
    //调用lua脚本
    stringRedisTemplate.execute(
        UNLOCK_SCRIPT,
        Collections.singletonList(KEY_PREFIX + name),ID_PREFIX  + Thread.currentThread().getId());
  }*/
  @Override
  public Result seckillVoucher(Long voucherId) {


    //1.查询优惠卷
    SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
    //2.判断秒杀是否开始
    if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
      //尚未开始
      return Result.fail("秒杀尚未开始！");
    }
    //3.判断秒杀是否已经结束
    if(voucher.getEndTime().isBefore(LocalDateTime.now())){
     //尚未开始
     return Result.fail("秒杀已经结束!");
    }
    //4.判断库存是否充足
    if(voucher.getStock()<1){
      //库存不足
      return Result.fail("库存不足!");
    }
    //5.扣减库存
    //5.一人一单逻辑
   // Long userId = UserHolder.getUser().getId();
    Long userId = UserHolder.getUser().getId();
    //创建锁对象
    //SimpleRedisLock lock = new SimpleRedisLock("order:" + userId,stringRedisTemplate);
    RLock lock = redissonClient.getLock("lock:order:" + userId);
    //获取锁对象
   // boolean isLock = lock.tryLock(1200);
    boolean isLock = lock.tryLock();
    //加锁失败
    if(!isLock){
      return Result.fail("不允许重复下单");
    }
    try{
      //获取代理对象(事务)
      IVoucherService proxy = (IVoucherService) AopContext.currentProxy();
      return proxy.createVoucherOrder(voucherId);
    } finally {
      lock.unlock();
    }
//    synchronized (userId.toString().intern()){
//      //获取代理对象
//      IVoucherService proxy = (IVoucherService) AopContext.currentProxy();
//      return proxy.createVoucherOrder(voucherId);
//      //return this.createVoucherOrder(voucherId);
//    }

    int count = query().eq("user_id",userId).eq("voucher_id",voucherId).count();
    //5.2判断是否存在
    if (count > 0){
      //用户已经购买过了
      return Result.fail("用户已经购买过一次");
    }
    //6.扣除库存
    boolean success = seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id",voucherId).update();
    if(!success){
      //扣减库存
      return Result.fail("库存不足!");
    }

    //6.创建订单
    VoucherOrder voucherOrder = new VoucherOrder();
    //6.1 订单id
    long orderId = redisIdWorker.nextId("order");
    voucherOrder.setId(orderId);
    voucherOrder.setUserId(userId);
    //6.2用户id
    //Long userId = UserHolder.getUser().getId();
    //voucherOrder.setUserId(userId);
    //6.3代金券id
    voucherOrder.setVoucherId(voucherId);
    save(voucherOrder);

    //return null;
    return Result.ok(orderId);
  }


  @Transactional
  public synchronized Result createVoucherOrder(Long voucherId){

    Long userId = UserHolder.getUser().getId();
    //5.1查询订单
    int count = query().eq("user_id",userId).eq("voucher_id",voucherId).count();
    //5.2判断是否存在
    if(count > 0){
      //用户已经购买过了
      return Result.fail("用户已经够改名过了一次");
    }

    //6.扣减库存
    boolean success = seckillVoucherService.update().setSql("stock = stock - 1")
        .eq("voucher_id",voucherId).gt("stock",0)// where id  = ? and stock > 0
        .update();
    if(!success){
      //扣减失败
      return Result.fail("库存不足");
    }

    //7.创建订单
    VoucherOrder voucherOrder = new VoucherOrder();
    //7.1订单id
    long orderId = redisIdWorker.nextId("order");
    voucherOrder.setId(orderId);
    //7.2用户id
    voucherOrder.setUserId(userId);
    //7.3代金券id
    voucherOrder.setVoucherId(voucherId);
    save(voucherOrder);

    //7.返回订单id
    return Result.ok(orderId);
  }


  @Override
  @Transactional
  public void addSeckillVouncher(Voucher voucher){
    //保存优惠卷
    save(voucher);
    //保存秒杀信息
    SeckillVoucher seckillVoucher = new SeckillVoucher();
    seckillVoucher.setVoucherId(voucher.getId());
    seckillVoucher.setStock(voucher.getStock());
  }
}
