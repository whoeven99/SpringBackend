package com.bogda.common.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class RetryUtilsTest {

    @Test
    void testRetryWithParamSuccessOnFirstAttempt() {
        AtomicInteger attempts = new AtomicInteger(0);
        Function<String, Boolean> task = (input) -> {
            attempts.incrementAndGet();
            return true;
        };

        boolean result = RetryUtils.retryWithParam(task, "test", 3, 10, 100);
        assertTrue(result);
        assertEquals(1, attempts.get());
    }

    @Test
    void testRetryWithParamSuccessOnSecondAttempt() {
        AtomicInteger attempts = new AtomicInteger(0);
        Function<String, Boolean> task = (input) -> {
            attempts.incrementAndGet();
            return attempts.get() >= 2;
        };

        boolean result = RetryUtils.retryWithParam(task, "test", 3, 10, 100);
        assertTrue(result);
        assertEquals(2, attempts.get());
    }

    @Test
    void testRetryWithParamFailureAfterMaxRetries() {
        AtomicInteger attempts = new AtomicInteger(0);
        Function<String, Boolean> task = (input) -> {
            attempts.incrementAndGet();
            return false;
        };

        boolean result = RetryUtils.retryWithParam(task, "test", 3, 10, 100);
        assertFalse(result);
        assertEquals(3, attempts.get());
    }

    @Test
    void testRetryWithParamWithException() {
        AtomicInteger attempts = new AtomicInteger(0);
        Function<String, Boolean> task = (input) -> {
            attempts.incrementAndGet();
            if (attempts.get() < 2) {
                throw new RuntimeException("Test exception");
            }
            return true;
        };

        boolean result = RetryUtils.retryWithParam(task, "test", 3, 10, 100);
        assertTrue(result);
        assertEquals(2, attempts.get());
    }

    @Test
    void testRetryWithParamWithExceptionFailure() {
        AtomicInteger attempts = new AtomicInteger(0);
        Function<String, Boolean> task = (input) -> {
            attempts.incrementAndGet();
            throw new RuntimeException("Test exception");
        };

        boolean result = RetryUtils.retryWithParam(task, "test", 2, 10, 100);
        assertFalse(result);
        assertEquals(2, attempts.get());
    }

    @Test
    void testRetryWithParamWithZeroMaxRetries() {
        AtomicInteger attempts = new AtomicInteger(0);
        Function<String, Boolean> task = (input) -> {
            attempts.incrementAndGet();
            return true;
        };

        boolean result = RetryUtils.retryWithParam(task, "test", 0, 10, 100);
        assertFalse(result);
        assertEquals(0, attempts.get());
    }

    @Test
    void testRetryWithParamWithExponentialBackoff() {
        AtomicInteger attempts = new AtomicInteger(0);
        long startTime = System.currentTimeMillis();
        Function<String, Boolean> task = (input) -> {
            attempts.incrementAndGet();
            return attempts.get() >= 3;
        };

        boolean result = RetryUtils.retryWithParam(task, "test", 3, 50, 200);
        long endTime = System.currentTimeMillis();
        
        assertTrue(result);
        assertEquals(3, attempts.get());
        // 验证有延迟发生（至少应该有一些延迟）
        assertTrue(endTime - startTime >= 50);
    }
}

