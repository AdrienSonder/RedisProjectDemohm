import redis.clients.jedis.Jedis;

import java.io.PrintStream;

public class Ping {
    public static void main(String[] args) {
        Jedis jedis1 = new Jedis("192.168.120.128",6379);
        System.out.println("连接成功");
        String response = jedis1.ping();
      //  PrintStream printf = System.out.printf("服务正在运行：%s%n", Jedis.ping());
        System.out.println("服务正在运行:" + response);
    }
}
