package com.bogda.common.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RedisKeyUtilsTest {

    @Test
    void testTranslateCacheKeyTemplate() {
        assertNotNull(RedisKeyUtils.TRANSLATE_CACHE_KEY_TEMPLATE);
        assertEquals("tc:{targetCode}:{source}", RedisKeyUtils.TRANSLATE_CACHE_KEY_TEMPLATE);
    }

    @Test
    void testDay14Constant() {
        assertEquals(1209600L, RedisKeyUtils.DAY_14);
    }

    @Test
    void testDay1Constant() {
        assertEquals(86400L, RedisKeyUtils.DAY_1);
    }
}

