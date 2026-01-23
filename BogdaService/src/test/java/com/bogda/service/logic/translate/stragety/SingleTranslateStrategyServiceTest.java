package com.bogda.service.logic.translate.stragety;

import com.bogda.common.TranslateContext;
import com.bogda.common.contants.TranslateConstants;
import com.bogda.common.entity.DO.GlossaryDO;
import com.bogda.service.logic.GlossaryService;
import com.bogda.service.logic.RedisProcessService;
import com.bogda.service.logic.redis.TranslateTaskMonitorV2RedisService;
import com.bogda.service.logic.translate.ModelTranslateService;
import com.bogda.common.utils.StringUtils;
import kotlin.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SingleTranslateStrategyServiceTest {

    @Mock
    private ModelTranslateService modelTranslateService;

    @Mock
    private RedisProcessService redisProcessService;

    @Mock
    private TranslateTaskMonitorV2RedisService translateTaskMonitorV2RedisService;

    @InjectMocks
    private SingleTranslateStrategyService singleTranslateStrategyService;

    private TranslateContext context;
    private String testContent;
    private String testTarget;
    private String testAiModel;

    @BeforeEach
    void setUp() {
        testContent = "Hello, world!";
        testTarget = "zh";
        testAiModel = "gemini-3-flash";

        context = new TranslateContext(testContent, testTarget, "TEXT", "test-key", new HashMap<>(), testAiModel);
    }

    @Test
    void testGetType_ShouldReturnSingle() {
        // When
        String type = singleTranslateStrategyService.getType();

        // Then
        assertEquals("SINGLE", type);
    }

    @Test
    void testTranslate_WithCachedValue_ShouldUseCache() {
        // Given
        String cachedValue = "你好，世界！";
        when(redisProcessService.getCacheData(testTarget, testContent)).thenReturn(cachedValue);

        // When
        singleTranslateStrategyService.translate(context);

        // Then
        assertTrue(context.isCached());
        assertEquals(cachedValue, context.getTranslatedContent());
        assertEquals("单条文本翻译-缓存命中", context.getStrategy());
        verify(translateTaskMonitorV2RedisService).addCacheCount(testContent);
        verifyNoInteractions(modelTranslateService);
    }

    @Test
    void testTranslate_WithGlossary_ShouldUseGlossaryPrompt() {
        // Given
        Map<String, GlossaryDO> glossaryMap = new HashMap<>();
        glossaryMap.put("Hello", new GlossaryDO("Hello", "你好", 0));

        context.setGlossaryMap(glossaryMap);
        when(redisProcessService.getCacheData(testTarget, testContent)).thenReturn(null);

        try (MockedStatic<GlossaryService> mockedGlossary = mockStatic(GlossaryService.class)) {
            mockedGlossary.when(() -> GlossaryService.hasGlossary(eq(testContent), anyMap(), anyMap())).thenReturn(true);
            mockedGlossary.when(() -> GlossaryService.convertMapToText(anyMap(), eq(testContent))).thenReturn("Hello -> 你好");

            Pair<String, Integer> mockPair = new Pair<>("你好，世界！", 50);
            when(modelTranslateService.modelTranslate(eq(testAiModel), anyString(), eq(testTarget), eq(testContent)))
                    .thenReturn(mockPair);

            // When
            singleTranslateStrategyService.translate(context);

            // Then
            assertEquals("语法表单条翻译", context.getStrategy());
            assertNotNull(context.getPrompt());
            verify(modelTranslateService).modelTranslate(eq(testAiModel), anyString(), eq(testTarget), eq(testContent));
        }
    }

    @Test
    void testTranslate_WithNormalText_ShouldUseNormalPrompt() {
        // Given
        context.setGlossaryMap(new HashMap<>());
        when(redisProcessService.getCacheData(testTarget, testContent)).thenReturn(null);

        try (MockedStatic<GlossaryService> mockedGlossary = mockStatic(GlossaryService.class)) {
            mockedGlossary.when(() -> GlossaryService.hasGlossary(anyString(), anyMap(), anyMap())).thenReturn(false);

            Pair<String, Integer> mockPair = new Pair<>("你好，世界！", 50);
            when(modelTranslateService.modelTranslate(eq(testAiModel), anyString(), eq(testTarget), eq(testContent)))
                    .thenReturn(mockPair);

            // When
            singleTranslateStrategyService.translate(context);

            // Then
            assertEquals("普通单条文本翻译", context.getStrategy());
            assertNotNull(context.getPrompt());
            verify(modelTranslateService).modelTranslate(eq(testAiModel), anyString(), eq(testTarget), eq(testContent));
            verify(redisProcessService).setCacheData(testTarget, "你好，世界！", testContent);
        }
    }

    @Test
    void testTranslate_WithHandleType_ShouldSetHandleStrategy() {
        // Given
        context.setShopifyTextType(TranslateConstants.URI);
        context.setShopifyTextKey("handle");
        context.setContent("hello-world-product");

        context.setGlossaryMap(new HashMap<>());
        when(redisProcessService.getCacheData(testTarget, context.getContent())).thenReturn(null);

        try (MockedStatic<GlossaryService> mockedGlossary = mockStatic(GlossaryService.class);
             MockedStatic<StringUtils> mockedStringUtils = mockStatic(StringUtils.class)) {

            mockedGlossary.when(() -> GlossaryService.hasGlossary(anyString(), anyMap(), anyMap())).thenReturn(false);
            mockedStringUtils.when(() -> StringUtils.replaceHyphensWithSpaces("hello-world-product"))
                    .thenReturn("hello world product");

            Pair<String, Integer> mockPair = new Pair<>("你好世界产品", 50);
            when(modelTranslateService.modelTranslate(eq(testAiModel), anyString(), eq(testTarget), eq(context.getContent())))
                    .thenReturn(mockPair);

            // When
            singleTranslateStrategyService.translate(context);

            // Then
            assertEquals("Handle 长文本翻译", context.getStrategy());
            assertNotNull(context.getPrompt());
        }
    }

    @Test
    void testTranslate_WithNullTranslation_ShouldNotSetContent() {
        // Given
        context.setGlossaryMap(new HashMap<>());
        when(redisProcessService.getCacheData(testTarget, testContent)).thenReturn(null);

        try (MockedStatic<GlossaryService> mockedGlossary = mockStatic(GlossaryService.class)) {
            mockedGlossary.when(() -> GlossaryService.hasGlossary(anyString(), anyMap(), anyMap())).thenReturn(false);

            when(modelTranslateService.modelTranslate(eq(testAiModel), anyString(), eq(testTarget), eq(testContent)))
                    .thenReturn(null);

            // When
            singleTranslateStrategyService.translate(context);

            // Then
            assertNull(context.getTranslatedContent());
            verify(redisProcessService, never()).setCacheData(anyString(), anyString(), anyString());
        }
    }

    @Test
    void testFinishAndGetJsonRecord_ShouldSetVariables() {
        // Given
        context.setStrategy("普通单条文本翻译");
        context.setUsedToken(100);
        context.setCached(true);
        context.setTranslatedChars(50);
        context.setStartTime(System.currentTimeMillis() - 1000);

        Map<String, GlossaryDO> usedGlossary = new HashMap<>();
        usedGlossary.put("Hello", new GlossaryDO("Hello", "你好", 0));
        context.setUsedGlossaryMap(usedGlossary);

        // When
        singleTranslateStrategyService.finishAndGetJsonRecord(context);

        // Then
        assertNotNull(context.getTranslateVariables());
        assertEquals("普通单条文本翻译", context.getTranslateVariables().get("strategy"));
        assertEquals("100", context.getTranslateVariables().get("usedToken"));
        assertEquals("true", context.getTranslateVariables().get("isCached"));
        assertEquals("50", context.getTranslateVariables().get("translatedChars"));
        assertEquals("Hello", context.getTranslateVariables().get("usedGlossary"));
        assertNotNull(context.getTranslatedTime());
    }

    @Test
    void testFinishAndGetJsonRecord_WithoutGlossary_ShouldNotIncludeGlossary() {
        // Given
        context.setStrategy("普通单条文本翻译");
        context.setUsedToken(100);
        context.setCached(false);
        context.setTranslatedChars(50);
        context.setStartTime(System.currentTimeMillis() - 1000);
        context.setUsedGlossaryMap(new HashMap<>());

        // When
        singleTranslateStrategyService.finishAndGetJsonRecord(context);

        // Then
        assertNotNull(context.getTranslateVariables());
        assertFalse(context.getTranslateVariables().containsKey("usedGlossary"));
    }
}

