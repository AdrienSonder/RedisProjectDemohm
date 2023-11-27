package com.hmdp.service.impl;

/*
import static com.hmdp.utils.SimpleRedisLock.ID_PREFIX;
import static com.hmdp.utils.SimpleRedisLock.KEY_PREFIX;
*/

import static com.hmdp.utils.RedisConstants.SECKILL_STOCK_KEY;

import cn.hutool.core.bean.BeanUtil;
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
import com.sun.javafx.collections.MappingChange.Map;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import net.bytebuddy.build.Plugin.Factory.Simple;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.transaction.annotation.Transactional;

public class VoucherOrderServiceImpl extends
    ServiceImpl<VoucherOrderMapper, VoucherOrder> implements
    IVoucherOrderService {

  //异步处理线程池
  private static final ExecutorService SECKILL_ODER_EXECUTOR = Executors.newSingleThreadExecutor();


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

  /**
   *
   */
  private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
  static {
    UNLOCK_SCRIPT = new DefaultRedisScript<>();
    UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
    UNLOCK_SCRIPT.setResultType(Long.class);
  }

  private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

  //在类初始化之后执行，因为当这个类初始化好了之后，随时都有可能要执行
  @PostConstruct
  private void init(){
      SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
  }


  //用于线程池处理的任务
  //当初始化完毕之后，就会拿去从队列中去拿信息
  private class VoucherOrderHandler implements Runnable{


    /**
     * When an object implementing interface <code>Runnable</code> is used to create a thread,
     * starting the thread causes the object's
     * <code>run</code> method to be called in that separately executing
     * thread.
     * <p>
     * The general contract of the method <code>run</code> is that it may take any action
     * whatsoever.
     *
     * @see Thread#run()
     */
    @Override
    public void run() {
        while(true){
          try{
            //1.从队列中获取订单信息
           // VoucherOrder voucherOrder = orderTasks.take();
            //1.获取消息队列中的订单信息
            List<MapRecord<String,Object,Object>> list = stringRedisTemplate.opsForStream().read(
                Consumer.from("g1","c1"));
            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2));

            StreamOffset.create("stream.orders", ReadOffset.lastConsumed());
            //2.判断订单信息是否为空
            if(list == null || list.isEmpty()){
              //如果为null,说明没有消息，继续下一次循环
              continue;
            }
            //解析数据
            MapRecord<String,Object,Object> record = list.get(0);
            Map<Object,Object> value = record.getValue();
            VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value,new VoucherOrder(),true);
            //3.创建订单
            createVoucherOrder(voucherOrder);
            //4.确认消息 XACK
            stringRedisTemplate.opsForStream().acknowledge("s1","g1",record.getId());

           /* //2.创建订单
            handlerVoucherOrder(voucherOrder);*/
          } catch (Exception e){
            log.error("订单处理异常",e);
            handlerPendingList();
          }
        }
    }

    private void handlerPendingList(){
      while(true){
        try{
          //1.获取pending-list中的订单嘻嘻你
          List<MapRecord<String,Object,Object>> list = stringRedisTemplate.opsForStream().read(Consumer.from("g1","c1"),StreamReadOptions.empty().count(1),StreamOffset.create("stream.orders",ReadOffset.from("0")));
          //2.判断订单信息是否为空
          if(list == null || list.isEmpty()){
            //如果为null,说明没有异常消息，结束循环
            break;
          }
          //解析数据
          MapRecord<String,Object,Object> record = list.get(0);
          Map<Object,Object> value = record.getValue();
          VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value,new VoucherOrder(),true);
          //3.创建订单
          createVoucherOrder(voucherOrder);
          //确认XACK
          stringRedisTemplate.opsForStream().acknowledge("s1","g1",record.getId());
        } catch (Exception e){
          log.error("处理pendding订单异常",e);

          try{
            Thread.sleep(20);
          } catch (Exception e){
            e.printStackTrace();
          }
        }
      }
    }

    private void handlerVoucherOrder(VoucherOrder voucherOrder){
      //1.获取用户
      Long userId = voucherOrder.getUserId();
      //2.创建锁对象
      RLock redisLock = redissonClient.getLock("lock:order:" + userId);
      //3.尝试获取锁对象
      boolean isLock = redisLock.lock();
      //判断是否获取锁成功
      if(!isLock){
        //获取锁失败，直接返回失败或者重试
        log.error("不允许重复下单！");
        return;
      }
      try{
        //由于spring 的事务是放在threadLocaL中的，此时是多线程，事务会失效
        proxy.createVoucherOrder(voucherOrder);
       } finally {
        //释放锁
        redisLock.unlock();
      }
    }

    //a
    private BlockingDeque<VoucherOrder> ordersTasks = new ArrayBlockingQueue<>(1024*1024);

    @Override
    public Result seckillVoucher(Long voucherId) {
      Long userId = UserHolder.getUser().getId();
      long orderId = redisIdWorker.nextId("order");
      // 1.执行lua脚本
      Long result = stringRedisTemplate.execute(
          SECKILL_SCRIPT,
          Collections.emptyList(),
          voucherId.toString(), userId.toString(), String.valueOf(orderId)
      );
      int r = result.intValue();
      // 2.判断结果是否为0
      if (r != 0) {
        // 2.1.不为0 ，代表没有购买资格
        return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
      }
      VoucherOrder voucherOrder = new VoucherOrder();
      // 2.3.订单id
      long orderId = redisIdWorker.nextId("order");
      voucherOrder.setId(orderId);
      // 2.4.用户id
      voucherOrder.setUserId(userId);
      // 2.5.代金券id
      voucherOrder.setVoucherId(voucherId);
      // 2.6.放入阻塞队列
      orderTasks.add(voucherOrder);
      //3.获取代理对象
      proxy = (IVoucherOrderService)AopContext.currentProxy();
      //4.返回订单id
      return Result.ok(orderId);
    }

    @Transactional
    public  void createVoucherOrder(VoucherOrder voucherOrder) {
      Long userId = voucherOrder.getUserId();
      // 5.1.查询订单
      int count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
      // 5.2.判断是否存在
      if (count > 0) {
        // 用户已经购买过了
        log.error("用户已经购买过了");
        return ;
      }

      // 6.扣减库存
      boolean success = seckillVoucherService.update()
          .setSql("stock = stock - 1") // set stock = stock - 1
          .eq("voucher_id", voucherOrder.getVoucherId()).gt("stock", 0) // where id = ? and stock > 0
          .update();
      if (!success) {
        // 扣减失败
        log.error("库存不足");
        return ;
      }
      save(voucherOrder);

    }

  }
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
    seckillVoucher.setBeginTime(voucher.getBeginTime());
    seckillVoucher.setEndTime(voucher.getEndTime());
    seckillVoucherService.save(seckillVoucher);

    stringRedisTemplate.opsForValue().set(SECKILL_STOCK_KEY + voucher.getId(),voucher.getStock().toString());
  }

  public Result seckillVoucher(Long voucherId){
    //获取用户
    Long userId = UserHolder.getUser().getId();
    long orderId = redisIdWorker.nextId("order");
    //1.执行脚本
    Long result = stringRedisTemplate.execute(SECKILL_SCRIPT,Collections.emptyList(),voucherId.toString(),String.valueOf(orderId));
    int r = result.intValue();
    if (r != 0){
      //2.1 不为0,代表没有购买资格
      return Result.fail(r == 1 ? "库存不足": "不能重复下单");
    }
    //TODO 保存阻塞队列
    //返回订单id
    return Result.ok(orderId);
  }

}
