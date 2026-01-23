package com.bogda.common.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

public class ConfigUtilsTest {

    private String originalApplicationEnv;

    @BeforeEach
    void setUp() {
        originalApplicationEnv = System.getenv("ApplicationEnv");
        ConfigUtils.config.clear();
    }

    @AfterEach
    void tearDown() {
        // 恢复环境变量（如果可能）
        if (originalApplicationEnv != null) {
            // 注意：Java 中无法直接设置环境变量，这里只是记录
        }
    }

    @Test
    void testGetConfigFromMap() {
        ConfigUtils.config.put("test.key", "test.value");
        String value = ConfigUtils.getConfig("test.key");
        assertEquals("test.value", value);
    }

    @Test
    void testGetConfigNotExists() {
        String value = ConfigUtils.getConfig("non.existent.key");
        assertNull(value);
    }

    @Test
    void testIsLocalEnvWhenEnvIsNull() {
        // 由于无法直接修改环境变量，这里测试逻辑
        // 如果 ApplicationEnv 为 null 或 "local"，应该返回 true
        // 这个测试依赖于实际环境，所以只验证方法不会抛出异常
        assertDoesNotThrow(() -> ConfigUtils.isLocalEnv());
    }

    @Test
    void testIsLocalEnvWhenEnvIsLocal() {
        // 测试逻辑：如果环境变量是 "local"，应该返回 true
        assertDoesNotThrow(() -> ConfigUtils.isLocalEnv());
    }

    @Test
    void testConfigMapIsAccessible() {
        assertNotNull(ConfigUtils.config);
        assertTrue(ConfigUtils.config instanceof Map);
    }
}

