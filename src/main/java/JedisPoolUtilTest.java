import org.junit.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class JedisPoolUtilTest {

    JedisPool jedisPool = JedisPoolUtil.getJedisPoolInstance();

    @Test
    public void test1(){
        JedisPool A = JedisPoolUtil.getJedisPoolInstance();
        JedisPool B = JedisPoolUtil.getJedisPoolInstance();
        System.out.println( A == B);
    }

    @Test
    public void test2(){
        Jedis jedis = null;

        try {
            jedis = jedisPool.getResource();//获取Redis连接对象
            jedis.set("key1","value111");
            System.out.println(jedis.get("key1"));
        } finally {
            jedis.close();//关闭redis连接
        }
    }

}
