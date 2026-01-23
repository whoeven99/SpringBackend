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
    void testDay15Constant() {
        assertEquals(2592000L, RedisKeyUtils.DAY_15);
    }

    @Test
    void testDay1Constant() {
        assertEquals(86400L, RedisKeyUtils.DAY_1);
    }

    @Test
    void testDataReportKeyTemplate() {
        assertNotNull(RedisKeyUtils.DATA_REPORT_KEY_TEMPLATE);
        assertEquals("dr:{shopName}:{language}:{yyyyMMdd}", RedisKeyUtils.DATA_REPORT_KEY_TEMPLATE);
    }

    @Test
    void testDataReportKeyTemplateKeys() {
        assertNotNull(RedisKeyUtils.DATA_REPORT_KEY_TEMPLATE_KEYS);
        assertEquals("drs:{shopName}:keys", RedisKeyUtils.DATA_REPORT_KEY_TEMPLATE_KEYS);
    }

    @Test
    void testClientIdSet() {
        assertNotNull(RedisKeyUtils.CLIENT_ID_SET);
        assertEquals("ci:{shopName}:{language}:{yyyyMMdd}:{eventName}", RedisKeyUtils.CLIENT_ID_SET);
    }

    @Test
    void testStoppedFlag() {
        assertNotNull(RedisKeyUtils.STOPPED_FLAG);
        assertEquals("tsk:stp:{shopName}", RedisKeyUtils.STOPPED_FLAG);
    }

    @Test
    void testStoppedFlagSingle() {
        assertNotNull(RedisKeyUtils.STOPPED_FLAG_SINGLE);
        assertEquals("tsk:stp:{shopName}:{InitialId}", RedisKeyUtils.STOPPED_FLAG_SINGLE);
    }
}

