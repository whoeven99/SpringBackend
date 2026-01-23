package com.bogda.common.utils;

import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ShopifyUtilsTest {

    @Test
    void testIsQueryValidWithValidData() {
        String queryData = "{\"node\":{\"id\":\"123\",\"name\":\"test\"}}";
        JSONObject result = ShopifyUtils.isQueryValid(queryData);
        assertNotNull(result);
        assertEquals("123", result.getString("id"));
        assertEquals("test", result.getString("name"));
    }

    @Test
    void testIsQueryValidWithNullNode() {
        String queryData = "{\"node\":null}";
        JSONObject result = ShopifyUtils.isQueryValid(queryData);
        assertNull(result);
    }

    @Test
    void testIsQueryValidWithEmptyNode() {
        String queryData = "{\"node\":{}}";
        JSONObject result = ShopifyUtils.isQueryValid(queryData);
        assertNull(result);
    }

    @Test
    void testIsQueryValidWithEmptyRoot() {
        String queryData = "{}";
        JSONObject result = ShopifyUtils.isQueryValid(queryData);
        assertNull(result);
    }

    @Test
    void testIsQueryValidWithMissingNode() {
        String queryData = "{\"other\":\"value\"}";
        JSONObject result = ShopifyUtils.isQueryValid(queryData);
        assertNull(result);
    }

    @Test
    void testGetAmount() {
        assertEquals(100000, ShopifyUtils.getAmount("50 extra times"));
        assertEquals(200000, ShopifyUtils.getAmount("100 extra times"));
        assertEquals(400000, ShopifyUtils.getAmount("200 extra times"));
        assertEquals(600000, ShopifyUtils.getAmount("300 extra times"));
        assertEquals(1000000, ShopifyUtils.getAmount("500 extra times"));
        assertEquals(2000000, ShopifyUtils.getAmount("1000 extra times"));
        assertEquals(4000000, ShopifyUtils.getAmount("2000 extra times"));
        assertEquals(6000000, ShopifyUtils.getAmount("3000 extra times"));
    }

    @Test
    void testGetAmountWithUnknown() {
        assertEquals(0, ShopifyUtils.getAmount("unknown"));
        assertEquals(0, ShopifyUtils.getAmount(""));
    }

    @Test
    void testGetAmountWithNull() {
        assertThrows(NullPointerException.class, () -> {
            ShopifyUtils.getAmount(null);
        });
    }

    @Test
    void testGetNumberFormat() {
        assertEquals("1,234", ShopifyUtils.getNumberFormat("1234"));
        assertEquals("10,000", ShopifyUtils.getNumberFormat("10000"));
        assertEquals("1,000,000", ShopifyUtils.getNumberFormat("1000000"));
    }

    @Test
    void testGetNumberFormatWithNull() {
        assertNull(ShopifyUtils.getNumberFormat(null));
    }

    @Test
    void testGetNumberFormatWithEmptyString() {
        assertEquals("", ShopifyUtils.getNumberFormat(""));
    }

    @Test
    void testGetNumberFormatWithZero() {
        assertEquals("0", ShopifyUtils.getNumberFormat("0"));
    }

    @Test
    void testGetNumberFormatWithLargeNumber() {
        String result = ShopifyUtils.getNumberFormat("1234567890");
        assertNotNull(result);
        assertTrue(result.contains(","));
    }
}

