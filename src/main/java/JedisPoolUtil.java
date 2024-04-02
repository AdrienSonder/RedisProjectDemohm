import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

//封装Jedis 连接池 配置JedisConfig信息 通过单例RedisPool获取Redis对象
public class JedisPoolUtil {

    private static final String HOST = "192.168.120.128";
    private static final int PORT = 6379;
    private static final int MAX_TOTAL = 100;
    private static final int MAX_IDEL = 100;
    private static final int MAX_WAITMILLS = 10 * 100;
    private static volatile JedisPool jedisPool = null;
    private JedisPoolUtil(){}


    public static JedisPool getJedisPoolInstance(){
        if(jedisPool == null){
            synchronized (JedisPoolUtil.class){
                if(jedisPool == null){
                    JedisPoolConfig poolConfig = new JedisPoolConfig();
                    poolConfig.setMaxTotal(MAX_TOTAL);
                    poolConfig.setMaxIdle(MAX_IDEL);
                    poolConfig.setMaxWaitMillis(MAX_WAITMILLS);
                    poolConfig.setTestOnBorrow(true);//检查连接可用性 确保获取redis实例可用
                    jedisPool =  new JedisPool(poolConfig,HOST,PORT);
                }
            }
        }
        return jedisPool;
    }

    /**
     * 从连接池获取一个Jedis实例连接
     * @return
     */
    public static Jedis getJedisInstance(){
        return getJedisPoolInstance().getResource();
    }


    /**
     * 将Jedis对象归还连接池
     * @param jedis
     */
    public static void releaseJedis(Jedis jedis){
        if(jedis != null){
            jedis.close();// jedisPool.returnResourceObject(jedis)已废弃
        }
    }


}
