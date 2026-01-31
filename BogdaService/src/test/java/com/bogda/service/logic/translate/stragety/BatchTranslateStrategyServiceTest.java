package com.bogda.service.logic.translate.stragety;

import com.bogda.common.TranslateContext;
import com.bogda.common.entity.DO.GlossaryDO;
import com.bogda.service.integration.ALiYunTranslateIntegration;
import com.bogda.service.logic.GlossaryService;
import com.bogda.service.logic.RedisProcessService;
import com.bogda.service.logic.redis.TranslateTaskMonitorV2RedisService;
import com.bogda.service.logic.translate.ModelTranslateService;
import com.bogda.common.utils.JsonUtils;
import com.bogda.common.utils.StringUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import kotlin.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BatchTranslateStrategyServiceTest {

    @Mock
    private RedisProcessService redisProcessService;

    @Mock
    private ModelTranslateService modelTranslateService;

    @Mock
    private TranslateTaskMonitorV2RedisService translateTaskMonitorV2RedisService;

    @InjectMocks
    private BatchTranslateStrategyService batchTranslateStrategyService;

    private TranslateContext context;
    private String testTarget;
    private String testAiModel;

    @BeforeEach
    void setUp() {
        testTarget = "zh";
        testAiModel = "gemini-3-flash";

        Map<Integer, String> originalTextMap = new HashMap<>();
        originalTextMap.put(1, "Hello");
        originalTextMap.put(2, "World");

        context = new TranslateContext(originalTextMap, testTarget, new HashMap<>(), testAiModel);
    }

    @Test
    void testGetType_ShouldReturnBatch() {
        // When
        String type = batchTranslateStrategyService.getType();

        // Then
        assertEquals("BATCH", type);
    }

    @Test
    void testTranslate_WithCachedValues_ShouldUseCache() {
        // Given
        when(redisProcessService.getCacheData(testTarget, "Hello")).thenReturn("你好");
        when(redisProcessService.getCacheData(testTarget, "World")).thenReturn(null);
        context.setGlossaryMap(new HashMap<>());

        try (MockedStatic<GlossaryService> mockedGlossary = mockStatic(GlossaryService.class);
             MockedStatic<ALiYunTranslateIntegration> mockedAliyun = mockStatic(ALiYunTranslateIntegration.class)) {
            mockedGlossary.when(() -> GlossaryService.match(anyString(), anyMap())).thenReturn(null);
            mockedGlossary.when(() -> GlossaryService.hasGlossary(anyString(), anyMap(), anyMap())).thenReturn(false);
            
            // Mock calculateBaiLianToken 方法，避免 tokenizer 为 null 的 NPE
            mockedAliyun.when(() -> ALiYunTranslateIntegration.calculateBaiLianToken(anyString())).thenReturn(10);

            // When
            batchTranslateStrategyService.translate(context);

            // Then
            assertEquals("你好", context.getTranslatedTextMap().get(1));
            assertEquals(1, context.getCachedCount());
            verify(translateTaskMonitorV2RedisService).addCacheCount("Hello");
        }
    }

    @Test
    void testTranslate_WithGlossaryMatch_ShouldUseGlossary() {
        // Given
        Map<String, GlossaryDO> glossaryMap = new HashMap<>();
        glossaryMap.put("Hello", new GlossaryDO("Hello", "你好", 0));

        context.setGlossaryMap(glossaryMap);
        when(redisProcessService.getCacheData(anyString(), anyString())).thenReturn(null);

        try (MockedStatic<GlossaryService> mockedGlossary = mockStatic(GlossaryService.class);
             MockedStatic<ALiYunTranslateIntegration> mockedAliyun = mockStatic(ALiYunTranslateIntegration.class)) {
            mockedGlossary.when(() -> GlossaryService.match("Hello", glossaryMap)).thenReturn("你好");
            mockedGlossary.when(() -> GlossaryService.match("World", glossaryMap)).thenReturn(null);
            mockedGlossary.when(() -> GlossaryService.hasGlossary(anyString(), anyMap(), anyMap())).thenReturn(false);

            // Mock calculateBaiLianToken 方法，避免 tokenizer 为 null 的 NPE
            mockedAliyun.when(() -> ALiYunTranslateIntegration.calculateBaiLianToken(anyString())).thenReturn(10);

            // When
            batchTranslateStrategyService.translate(context);

            // Then
            assertEquals("你好", context.getTranslatedTextMap().get(1));
            assertEquals(1, context.getGlossaryCount());
        }
    }

    @Test
    void testTranslate_WithUncachedText_ShouldCallModelTranslate() {
        // Given
        when(redisProcessService.getCacheData(anyString(), anyString())).thenReturn(null);
        context.setGlossaryMap(new HashMap<>());

        try (MockedStatic<GlossaryService> mockedGlossary = mockStatic(GlossaryService.class)) {
            mockedGlossary.when(() -> GlossaryService.match(anyString(), anyMap())).thenReturn(null);
            mockedGlossary.when(() -> GlossaryService.hasGlossary(anyString(), anyMap(), anyMap())).thenReturn(false);

            Map<Integer, String> translatedMap = new HashMap<>();
            translatedMap.put(1, "你好");
            translatedMap.put(2, "世界");
            Pair<String, Integer> mockPair = new Pair<>("{\"1\":\"你好\",\"2\":\"世界\"}", 100);

            when(modelTranslateService.modelTranslate(eq(testAiModel), anyString(), eq(testTarget), anyMap()))
                    .thenReturn(mockPair);

            try (MockedStatic<ALiYunTranslateIntegration> mockedAliyun = mockStatic(ALiYunTranslateIntegration.class)) {
                mockedAliyun.when(() -> ALiYunTranslateIntegration.calculateBaiLianToken(anyString())).thenReturn(10);

                // When
                batchTranslateStrategyService.translate(context);

                // Then
                verify(modelTranslateService, atLeastOnce()).modelTranslate(eq(testAiModel), anyString(), eq(testTarget), anyMap());
                verify(redisProcessService, atLeastOnce()).setCacheData(eq(testTarget), anyString(), anyString());
            }
        }
    }

    @Test
    void testFinishAndGetJsonRecord_ShouldSetVariables() {
        // Given
        context.setStrategy("Batch json 翻译");
        context.setUsedToken(100);
        context.setCachedCount(2);
        context.setGlossaryCount(1);
        context.setTranslatedChars(50);
        context.setStartTime(System.currentTimeMillis() - 1000);

        // When
        batchTranslateStrategyService.finishAndGetJsonRecord(context);

        // Then
        assertNotNull(context.getTranslateVariables());
        assertEquals("Batch json 翻译", context.getTranslateVariables().get("strategy"));
        assertEquals("100", context.getTranslateVariables().get("usedToken"));
        assertEquals("2", context.getTranslateVariables().get("cachedCount"));
        assertEquals("1", context.getTranslateVariables().get("glossaryCount"));
        assertEquals("50", context.getTranslateVariables().get("translatedChars"));
        assertNotNull(context.getTranslatedTime());
    }

    @Test
    void testParseOutput_WithValidJson_ShouldReturnMap() {
        // Given
        String input = "{\"1\":\"你好\",\"2\":\"世界\"}";

        try (MockedStatic<StringUtils> mockedStringUtils = mockStatic(StringUtils.class);
             MockedStatic<JsonUtils> mockedJsonUtils = mockStatic(JsonUtils.class)) {

            mockedStringUtils.when(() -> StringUtils.extractJsonBlock(input)).thenReturn(input);

            LinkedHashMap<Integer, String> expectedMap = new LinkedHashMap<>();
            expectedMap.put(1, "你好");
            expectedMap.put(2, "世界");

            mockedJsonUtils.when(() -> JsonUtils.jsonToObjectWithNull(eq(input), any(TypeReference.class)))
                    .thenReturn(expectedMap);

            // When
            LinkedHashMap<Integer, String> result = BatchTranslateStrategyService.parseOutput(input);

            // Then
            assertNotNull(result);
            assertEquals(2, result.size());
            assertEquals("你好", result.get(1));
            assertEquals("世界", result.get(2));
        }
    }

    @Test
    void testParseOutput_WithNullInput_ShouldReturnNull() {
        // When
        LinkedHashMap<Integer, String> result = BatchTranslateStrategyService.parseOutput(null);

        // Then
        assertNull(result);
    }

    @Test
    void testParseOutput_WithInvalidJson_ShouldReturnNull() {
        // Given
        String input = "invalid json";

        try (MockedStatic<StringUtils> mockedStringUtils = mockStatic(StringUtils.class)) {
            mockedStringUtils.when(() -> StringUtils.extractJsonBlock(input)).thenReturn(null);

            // When
            LinkedHashMap<Integer, String> result = BatchTranslateStrategyService.parseOutput(input);

            // Then
            assertNull(result);
        }
    }

    @Test
    void testParseOutput_WithEmptyValues_ShouldFilterEmptyValues() {
        // Given
        String input = "{\"1\":\"你好\",\"2\":\"\",\"3\":\"   \"}";

        try (MockedStatic<StringUtils> mockedStringUtils = mockStatic(StringUtils.class);
             MockedStatic<JsonUtils> mockedJsonUtils = mockStatic(JsonUtils.class)) {

            mockedStringUtils.when(() -> StringUtils.extractJsonBlock(input)).thenReturn(input);

            LinkedHashMap<Integer, String> mapWithEmpty = new LinkedHashMap<>();
            mapWithEmpty.put(1, "你好");
            mapWithEmpty.put(2, "");
            mapWithEmpty.put(3, "   ");

            mockedJsonUtils.when(() -> JsonUtils.jsonToObjectWithNull(eq(input), any(TypeReference.class)))
                    .thenReturn(mapWithEmpty);

            // When
            LinkedHashMap<Integer, String> result = BatchTranslateStrategyService.parseOutput(input);

            // Then
            assertNotNull(result);
            assertEquals(1, result.size());
            assertTrue(result.containsKey(1));
            assertEquals("你好", result.get(1));
        }
    }
}

