import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class JedisPoolTest {

    private Logger logger = LoggerFactory.getLogger(JedisPoolTest.class);

    public static void main(String[] args) {
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        jedisPoolConfig.setMaxTotal(30);//最大连接数
        jedisPoolConfig.setMaxIdle(10);//最大空闲连接数
        jedisPoolConfig.setMaxWaitMillis(10*1000);//最大等待时间

        JedisPool jedisPool = new JedisPool(jedisPoolConfig,"192.168.120.128",6379);
        Jedis jedis = null;
        try{
            jedis = jedisPool.getResource();
            jedis.set("name","zsy");
            String str = jedis.get("name");
            System.out.println(str);
        }
        catch (Exception e){
            e.printStackTrace();
        }
        finally {
            if(jedis != null){
                jedis.close();
            }
        }


        if(jedisPool != null){
            jedisPool.close();
        }
    }
}
