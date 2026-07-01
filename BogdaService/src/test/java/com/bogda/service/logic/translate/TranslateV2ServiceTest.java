package com.bogda.service.logic.translate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TranslateV2ServiceTest {

    @InjectMocks
    private TranslateV2Service translateV2Service;

    @Test
    void testSortTranslateData_WithValidList_ShouldSortCorrectly() {
        List<String> inputList = Arrays.asList("PRODUCT", "COLLECTION", "PAGE");

        List<String> result = TranslateV2Service.sortTranslateData(inputList);

        assertNotNull(result);
        assertTrue(result.size() > 0);
    }

    @Test
    void testSortTranslateData_WithEmptyList_ShouldReturnEmptyList() {
        List<String> inputList = new ArrayList<>();

        List<String> result = TranslateV2Service.sortTranslateData(inputList);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testSortTranslateData_WithNullList_ShouldHandleGracefully() {
        assertThrows(NullPointerException.class, () -> TranslateV2Service.sortTranslateData(null));
    }

    @Test
    void testTestTranslate_WithQwenModel_ShouldCallAliYunIntegration() {
        Map<String, Object> map = new HashMap<>();
        map.put("model", "qwen3.6-plus");
        map.put("prompt", "Translate: {{SOURCE_LANGUAGE_LIST}} to {{TARGET_LANGUAGE}}");
        map.put("target", "zh");
        map.put("json", "{\"1\":\"English\"}");

        Map<String, Object> result = translateV2Service.testTranslate(map);

        assertNotNull(result);
    }

    @Test
    void testTestTranslate_WithGeminiModel_ShouldCallGeminiIntegration() {
        Map<String, Object> map = new HashMap<>();
        map.put("model", "gemini-3-flash");
        map.put("prompt", "Translate this");
        map.put("target", "zh");
        map.put("json", "{}");

        Map<String, Object> result = translateV2Service.testTranslate(map);

        assertNotNull(result);
    }

    @Test
    void testTestTranslate_WithInvalidJson_ShouldReturnDefaultNullMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("model", "qwen3.6-plus");
        map.put("prompt", "Translate");
        map.put("target", "zh");
        map.put("json", "invalid-json");

        Map<String, Object> result = translateV2Service.testTranslate(map);

        assertNotNull(result);
        assertEquals("error", result.get("content"));
        assertEquals(0, result.get("allToken"));
    }

    @Test
    void testTestTranslate_WithEmptyLanguageMap_ShouldReturnDefaultNullMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("model", "qwen3.6-plus");
        map.put("prompt", "Translate");
        map.put("target", "zh");
        map.put("json", "{}");

        Map<String, Object> result = translateV2Service.testTranslate(map);

        assertNotNull(result);
        assertEquals("error", result.get("content"));
        assertEquals(0, result.get("allToken"));
    }
}
