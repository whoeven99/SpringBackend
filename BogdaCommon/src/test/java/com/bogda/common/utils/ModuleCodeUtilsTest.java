package com.bogda.common.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ModuleCodeUtilsTest {

    @Test
    void testGetModuleCodeWithCode2() {
        assertEquals("gpt-4.1", ModuleCodeUtils.getModuleCode("2"));
    }

    @Test
    void testGetModuleCodeWithDefault() {
        assertEquals("qwen-max", ModuleCodeUtils.getModuleCode("1"));
        assertEquals("qwen-max", ModuleCodeUtils.getModuleCode("3"));
        assertEquals("qwen-max", ModuleCodeUtils.getModuleCode("default"));
    }

    @Test
    void testMicrosoftTransformCode() {
        assertEquals("zh-Hans", ModuleCodeUtils.microsoftTransformCode("zh-CN"));
        assertEquals("zh-Hant", ModuleCodeUtils.microsoftTransformCode("zh-TW"));
        assertEquals("mn-Cyrl", ModuleCodeUtils.microsoftTransformCode("mn"));
        assertEquals("pt-br", ModuleCodeUtils.microsoftTransformCode("pt-BR"));
        assertEquals("pt-pt", ModuleCodeUtils.microsoftTransformCode("pt-PT"));
        assertEquals("sr-Cyrl", ModuleCodeUtils.microsoftTransformCode("sr"));
        assertEquals("run", ModuleCodeUtils.microsoftTransformCode("rn"));
    }

    @Test
    void testMicrosoftTransformCodeWithUnchanged() {
        assertEquals("en", ModuleCodeUtils.microsoftTransformCode("en"));
        assertEquals("fr", ModuleCodeUtils.microsoftTransformCode("fr"));
    }

    @Test
    void testHuoShanTransformCode() {
        assertEquals("zh", ModuleCodeUtils.huoShanTransformCode("zh-CN"));
        assertEquals("zh-Hant", ModuleCodeUtils.huoShanTransformCode("zh-TW"));
    }

    @Test
    void testHuoShanTransformCodeWithUnsupported() {
        assertEquals("#N/A", ModuleCodeUtils.huoShanTransformCode("ak"));
        assertEquals("#N/A", ModuleCodeUtils.huoShanTransformCode("as"));
        assertEquals("#N/A", ModuleCodeUtils.huoShanTransformCode("pt-BR"));
    }

    @Test
    void testHuoShanTransformCodeWithUnchanged() {
        assertEquals("en", ModuleCodeUtils.huoShanTransformCode("en"));
        assertEquals("fr", ModuleCodeUtils.huoShanTransformCode("fr"));
    }

    @Test
    void testQwenMtCode() {
        assertEquals("Chinese", ModuleCodeUtils.qwenMtCode("zh-CN"));
        assertEquals("English", ModuleCodeUtils.qwenMtCode("en"));
        assertEquals("Japanese", ModuleCodeUtils.qwenMtCode("ja"));
        assertEquals("Korean", ModuleCodeUtils.qwenMtCode("ko"));
        assertEquals("Thai", ModuleCodeUtils.qwenMtCode("th"));
        assertEquals("French", ModuleCodeUtils.qwenMtCode("fr"));
        assertEquals("German", ModuleCodeUtils.qwenMtCode("de"));
        assertEquals("Spanish", ModuleCodeUtils.qwenMtCode("es"));
        assertEquals("Arabic", ModuleCodeUtils.qwenMtCode("ar"));
        assertEquals("Indonesian", ModuleCodeUtils.qwenMtCode("id"));
        assertEquals("Vietnamese", ModuleCodeUtils.qwenMtCode("vi"));
        assertEquals("Portuguese", ModuleCodeUtils.qwenMtCode("pt-BR"));
        assertEquals("Italian", ModuleCodeUtils.qwenMtCode("it"));
        assertEquals("Dutch", ModuleCodeUtils.qwenMtCode("nl"));
        assertEquals("Russian", ModuleCodeUtils.qwenMtCode("ru"));
    }

    @Test
    void testQwenMtCodeWithUnchanged() {
        assertEquals("unknown", ModuleCodeUtils.qwenMtCode("unknown"));
    }

    @Test
    void testGetLanguageName() {
        assertEquals("English", ModuleCodeUtils.getLanguageName("en"));
        assertEquals("Chinese (Simplified)", ModuleCodeUtils.getLanguageName("zh-CN"));
        assertEquals("Chinese (Traditional)", ModuleCodeUtils.getLanguageName("zh-TW"));
        assertEquals("Japanese", ModuleCodeUtils.getLanguageName("ja"));
    }

    @Test
    void testGetLanguageNameWithUnknown() {
        assertEquals("unknown-code", ModuleCodeUtils.getLanguageName("unknown-code"));
    }

    @Test
    void testLanguageCodesSet() {
        assertTrue(ModuleCodeUtils.LANGUAGE_CODES.contains("ce"));
        assertTrue(ModuleCodeUtils.LANGUAGE_CODES.contains("kw"));
        assertTrue(ModuleCodeUtils.LANGUAGE_CODES.contains("ar-EG"));
        assertFalse(ModuleCodeUtils.LANGUAGE_CODES.contains("en"));
    }

    @Test
    void testAliImageMap() {
        assertNotNull(ModuleCodeUtils.aliImageMap);
        assertTrue(ModuleCodeUtils.aliImageMap.containsKey("zh"));
        assertTrue(ModuleCodeUtils.aliImageMap.containsKey("en"));
        assertTrue(ModuleCodeUtils.aliImageMap.get("zh").contains("en"));
        assertTrue(ModuleCodeUtils.aliImageMap.get("en").contains("zh"));
    }
}

