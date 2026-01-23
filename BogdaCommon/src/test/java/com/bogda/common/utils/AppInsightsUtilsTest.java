package com.bogda.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class AppInsightsUtilsTest {

    @BeforeEach
    void setUp() {
        // 由于 AppInsightsUtils 使用静态方法，这里主要是确保不会抛出异常
    }

    @Test
    void testTrackTraceWithMessage() {
        assertDoesNotThrow(() -> {
            AppInsightsUtils.trackTrace("Test message");
        });
    }

    @Test
    void testTrackTraceWithMessageAndArgs() {
        assertDoesNotThrow(() -> {
            AppInsightsUtils.trackTrace("Test message: %s", "arg1");
        });
    }

    @Test
    void testTrackException() {
        assertDoesNotThrow(() -> {
            Exception e = new RuntimeException("Test exception");
            AppInsightsUtils.trackException(e);
        });
    }

    @Test
    void testPrintTranslateCost() {
        assertDoesNotThrow(() -> {
            AppInsightsUtils.printTranslateCost(1000, 600, 400);
        });
    }

    @Test
    void testPrintTranslateCostWithZero() {
        assertDoesNotThrow(() -> {
            AppInsightsUtils.printTranslateCost(0, 0, 0);
        });
    }
}

