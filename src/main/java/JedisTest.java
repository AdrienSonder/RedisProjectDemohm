import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;

public class JedisTest {
    public static void main(String[] args) {
        Jedis jedis2 = new Jedis("192.168.120.128",6379);

        jedis2.set("HELLO","WORLD");
        String value = jedis2.get("HELLO");
        System.out.println(value);
        /*Logger logger = LoggerFactory.getLogger(JedisTest.class);
        logger.warn("从redis中拿到数据"+value);*/

        jedis2.hset("sadasd","dadasd","dasdasdas");
        String value2 = jedis2.hget("sadasd","dadasd");
        System.out.println(value2);

        jedis2.lpush("sddd","dasdasssssd");
      /*  String value3 = jedis2.lpop("sddd");
        System.out.println(value3);*/
    }
}
