package com.jjx.framework.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

/**
 * Redis测试工具类
 * 用于验证Redis连接和基本功能
 */
@Component
@Slf4j
public class RedisTestUtil {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 应用启动后自动测试Redis连接
     */
    @PostConstruct
    public void testRedisConnection() {
        try {
            // 测试字符串操作
            String testKey = "jjx:redis:test:connection";
            String testValue = "Redis connection test successful at " + System.currentTimeMillis();

            // 写入测试数据
            stringRedisTemplate.opsForValue().set(testKey, testValue, 60, TimeUnit.SECONDS);

            // 读取测试数据
            String retrievedValue = stringRedisTemplate.opsForValue().get(testKey);

            if (testValue.equals(retrievedValue)) {
                log.info("✅ Redis连接测试成功！配置正确，连接正常。");
                log.info("测试键: {}, 测试值: {}", testKey, retrievedValue);

                // 测试对象序列化
                testObjectSerialization();
            } else {
                log.warn("⚠️ Redis连接测试异常：读取的值与写入的值不匹配");
            }

        } catch (Exception e) {
            log.error("❌ Redis连接测试失败！请检查Redis配置和服务状态", e);
        }
    }

    /**
     * 测试对象序列化功能
     */
    private void testObjectSerialization() {
        try {
            String objectKey = "jjx:redis:test:object";
            TestObject testObject = new TestObject("测试对象", 123, System.currentTimeMillis());

            // 存储对象
            redisTemplate.opsForValue().set(objectKey, testObject, 30, TimeUnit.SECONDS);

            // 读取对象
            TestObject retrievedObject = (TestObject) redisTemplate.opsForValue().get(objectKey);

            if (retrievedObject != null &&
                testObject.getName().equals(retrievedObject.getName()) &&
                testObject.getValue() == retrievedObject.getValue()) {
                log.info("✅ Redis对象序列化测试成功！");
                log.info("测试对象: {}", retrievedObject);
            } else {
                log.warn("⚠️ Redis对象序列化测试异常：读取的对象与写入的对象不匹配");
            }

        } catch (Exception e) {
            log.error("❌ Redis对象序列化测试失败", e);
        }
    }

    /**
     * 测试用内部类
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class TestObject {
        private String name;
        private int value;
        private long timestamp;
    }

    /**
     * 手动测试Redis连接（可在需要时调用）
     */
    public boolean manualTest() {
        try {
            String manualKey = "jjx:redis:manual:test";
            String manualValue = "Manual test at " + System.currentTimeMillis();

            stringRedisTemplate.opsForValue().set(manualKey, manualValue, 10, TimeUnit.SECONDS);
            String result = stringRedisTemplate.opsForValue().get(manualKey);

            return manualValue.equals(result);
        } catch (Exception e) {
            log.error("手动Redis测试失败", e);
            return false;
        }
    }

    /**
     * 获取Redis信息
     */
    public String getRedisInfo() {
        try {
            // 获取Redis服务器信息
            Object info = redisTemplate.getConnectionFactory().getConnection().info();
            return "Redis连接正常，服务器信息已获取";
        } catch (Exception e) {
            return "获取Redis信息失败: " + e.getMessage();
        }
    }
}
