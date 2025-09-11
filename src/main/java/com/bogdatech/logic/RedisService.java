package com.bogdatech.logic;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.Map;

@Service
public class RedisService {
    @Autowired
    private JedisPool jedisPool; // 自动注入


   /**
    * 异步对redis进度条做原子递增
    * */
   @Async
   public long incrementProgressFieldData(String shopName, String targetCode, String field, int increment) {
       String key = "tr:" + shopName + ":" + targetCode;
       try (Jedis jedis = jedisPool.getResource()) {
           return jedis.hincrBy(key, field, increment);
       }
   }


   /**
    * redis进度条的获取
    * */
   public Map<String, String> getTranslationProgress(String shopName, String targetCode) {
       String key = "tr:" + shopName + ":" + targetCode;
       try (Jedis jedis = jedisPool.getResource()) {
           return jedis.hgetAll(key);
       }
   }

   /**
    * 翻译完后，删除redis进度条相关数据
    * */
   public void deleteTranslationProgress(String shopName, String targetCode) {
       String key = "tr:" + shopName + ":" + targetCode;
       try (Jedis jedis = jedisPool.getResource()) {
           jedis.del(key);
       }
   }

}
