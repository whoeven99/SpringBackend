package com.bogda.api;

import com.bogda.service.utils.TimeOutUtils;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TimeOutUtilsTest {

    @Test
    public void testCallWithTimeoutAndRetry() {
        AtomicInteger attemptCounter = new AtomicInteger(0);
        Supplier<String> task = () -> {
            attemptCounter.incrementAndGet();
            try {
                Thread.sleep(2000); // 模拟一个长时间运行的任务
            } catch (InterruptedException e) {
            }
            return "Success";
        };

        try {
            String result = TimeOutUtils.callWithTimeoutAndRetry(task, 1, TimeUnit.SECONDS, 3); // 超时时间为1秒，重试3次
            fail("Expected TimeoutException to be thrown");
        } catch (TimeoutException e) {
            assertNotNull(e, "TimeoutException should be thrown");
            assertEquals(3, attemptCounter.get(), "Task should be retried 3 times");
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }
    }

    @Test
    public void testChatGptRateLimiter() {
        AtomicInteger attemptCounter = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();
        Map<Integer, Long> attemptTimestamps = new HashMap<>();
        Supplier<String> task = () -> {
            attemptCounter.incrementAndGet();
            attemptTimestamps.put(attemptCounter.get(), System.currentTimeMillis() - startTime);
            return "Success";
        };

        try {
            for (int i = 0; i < 6; i++) {
                TimeOutUtils.callWithTimeoutAndRetry(task, 1, TimeUnit.SECONDS, 1,
                        TimeOutUtils.rateLimiter2);
            }
        } catch (Exception e) {
            fail("Unexpected exception: " + e.getMessage());
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // 验证任务执行次数
        assertEquals(6, attemptCounter.get(), "Task should be executed 5 times");

        // 验证时间  (6 - 1) / 2 QPS = 2.5 seconds
        assertTrue(duration >= 2500, "Execution time should respect QPS limit");
    }
}
