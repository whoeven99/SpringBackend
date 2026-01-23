package com.bogda.service.logic.translate.stragety;

import com.bogda.common.TranslateContext;
import com.bogda.common.entity.DO.GlossaryDO;
import com.bogda.service.logic.RedisProcessService;
import com.bogda.service.logic.redis.TranslateTaskMonitorV2RedisService;
import com.bogda.service.logic.translate.ModelTranslateService;
import kotlin.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bogda.service.integration.ALiYunTranslateIntegration;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;

@ExtendWith(MockitoExtension.class)
@DisplayName("BatchTranslateStrategyService 单元测试")
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
    private Map<Integer, String> originalTextMap;
    private Map<String, GlossaryDO> glossaryMap;

    @BeforeEach
    void setUp() {
        originalTextMap = new HashMap<>();
        glossaryMap = new HashMap<>();
        
        context = new TranslateContext(originalTextMap, "zh-CN", glossaryMap, "qwen-max");
    }

    @Test
    @DisplayName("测试缓存命中场景")
    void testTranslateWithCacheHit() {
        // 准备数据
        originalTextMap.put(1, "Hello");
        originalTextMap.put(2, "World");
        
        // Mock 缓存返回
        when(redisProcessService.getCacheData("zh-CN", "Hello")).thenReturn("你好");
        when(redisProcessService.getCacheData("zh-CN", "World")).thenReturn("世界");

        // 执行
        batchTranslateStrategyService.translate(context);

        // 验证
        assertEquals("Batch json 翻译", context.getStrategy());
        assertEquals("你好", context.getTranslatedTextMap().get(1));
        assertEquals("世界", context.getTranslatedTextMap().get(2));
        assertEquals(2, context.getCachedCount());
        verify(translateTaskMonitorV2RedisService, times(2)).addCacheCount(anyString());
        verify(modelTranslateService, never()).modelTranslate(anyString(), anyString(), anyString(), anyString());
        verify(modelTranslateService, never()).modelTranslate(anyString(), anyString(), anyString(), ArgumentMatchers.<Map<Integer, String>>any());
    }

    @Test
    @DisplayName("测试词汇表完全匹配场景")
    void testTranslateWithGlossaryExactMatch() {
        // 准备数据
        originalTextMap.put(1, "Hello");
        GlossaryDO glossary = new GlossaryDO("Hello", "你好", 0);
        glossaryMap.put("Hello", glossary);

        // Mock 缓存返回 null（未命中）
        when(redisProcessService.getCacheData("zh-CN", "Hello")).thenReturn(null);

        // 执行
        batchTranslateStrategyService.translate(context);

        // 验证
        assertEquals("你好", context.getTranslatedTextMap().get(1));
        assertEquals(1, context.getGlossaryCount());
        verify(modelTranslateService, never()).modelTranslate(anyString(), anyString(), anyString(), anyString());
        verify(modelTranslateService, never()).modelTranslate(anyString(), anyString(), anyString(), ArgumentMatchers.<Map<Integer, String>>any());
    }

    @Test
    @DisplayName("测试词汇表部分匹配（需要AI翻译）")
    void testTranslateWithGlossaryPartialMatch() {
        // 准备数据
        originalTextMap.put(1, "Hello World");
        GlossaryDO glossary = new GlossaryDO("Hello", "你好", 0);
        glossaryMap.put("Hello", glossary);

        // Mock 缓存返回 null
        when(redisProcessService.getCacheData("zh-CN", "Hello World")).thenReturn(null);

        // Mock AI 翻译返回 - 使用 Map<Integer, String> 类型避免方法歧义
        when(modelTranslateService.modelTranslate(eq("qwen-max"), anyString(), eq("zh-CN"), ArgumentMatchers.<Map<Integer, String>>any()))
                .thenReturn(new Pair<>("{\"1\":\"你好世界\"}", 100));

        // Mock 静态方法 calculateBaiLianToken
        try (MockedStatic<ALiYunTranslateIntegration> mockedStatic = mockStatic(ALiYunTranslateIntegration.class)) {
            mockedStatic.when(() -> ALiYunTranslateIntegration.calculateBaiLianToken(anyString()))
                    .thenAnswer(invocation -> {
                        String text = invocation.getArgument(0);
                        return text != null ? text.length() : 0;
                    });

            // 执行
            batchTranslateStrategyService.translate(context);

            // 验证（必须在 mock 作用域内）
            assertNotNull(context.getTranslatedTextMap().get(1));
            assertTrue(context.getGlossaryCount() > 0);
            verify(modelTranslateService, atLeastOnce()).modelTranslate(anyString(), anyString(), anyString(), ArgumentMatchers.<Map<Integer, String>>any());
        }
    }

    @Test
    @DisplayName("测试纯AI翻译场景")
    void testTranslateWithPureAI() {
        // 准备数据
        originalTextMap.put(1, "Hello");
        originalTextMap.put(2, "World");

        // Mock 缓存返回 null
        when(redisProcessService.getCacheData("zh-CN", "Hello")).thenReturn(null);
        when(redisProcessService.getCacheData("zh-CN", "World")).thenReturn(null);

        // Mock AI 翻译返回 - 使用 Map<Integer, String> 类型避免方法歧义
        when(modelTranslateService.modelTranslate(eq("qwen-max"), anyString(), eq("zh-CN"), ArgumentMatchers.<Map<Integer, String>>any()))
                .thenReturn(new Pair<>("{\"1\":\"你好\",\"2\":\"世界\"}", 200));

        // Mock 静态方法 calculateBaiLianToken
        try (MockedStatic<ALiYunTranslateIntegration> mockedStatic = mockStatic(ALiYunTranslateIntegration.class)) {
            mockedStatic.when(() -> ALiYunTranslateIntegration.calculateBaiLianToken(anyString()))
                    .thenAnswer(invocation -> {
                        String text = invocation.getArgument(0);
                        return text != null ? text.length() : 0;
                    });

            // 执行
            batchTranslateStrategyService.translate(context);

            // 验证（必须在 mock 作用域内）
            assertNotNull(context.getTranslatedTextMap().get(1));
            assertNotNull(context.getTranslatedTextMap().get(2));
            assertEquals(200, context.getUsedToken());
            verify(modelTranslateService, atLeastOnce()).modelTranslate(anyString(), anyString(), anyString(), ArgumentMatchers.<Map<Integer, String>>any());
            verify(redisProcessService, atLeastOnce()).setCacheData(eq("zh-CN"), anyString(), anyString());
        }
    }

    @Test
    @DisplayName("测试空值和空字符串处理")
    void testTranslateWithNullAndEmptyValues() {
        // 准备数据
        originalTextMap.put(1, "Hello");
        originalTextMap.put(2, null);
        originalTextMap.put(3, "");
        originalTextMap.put(4, "World");

        // Mock 缓存
        when(redisProcessService.getCacheData("zh-CN", "Hello")).thenReturn("你好");
        when(redisProcessService.getCacheData("zh-CN", "World")).thenReturn(null);

        // Mock AI 翻译 - 使用 Map<Integer, String> 类型避免方法歧义
        when(modelTranslateService.modelTranslate(eq("qwen-max"), anyString(), eq("zh-CN"), ArgumentMatchers.<Map<Integer, String>>any()))
                .thenReturn(new Pair<>("{\"4\":\"世界\"}", 100));

        // Mock 静态方法 calculateBaiLianToken
        try (MockedStatic<ALiYunTranslateIntegration> mockedStatic = mockStatic(ALiYunTranslateIntegration.class)) {
            mockedStatic.when(() -> ALiYunTranslateIntegration.calculateBaiLianToken(anyString()))
                    .thenAnswer(invocation -> {
                        String text = invocation.getArgument(0);
                        return text != null ? text.length() : 0;
                    });

            // 执行
            batchTranslateStrategyService.translate(context);

            // 验证（必须在 mock 作用域内）
            assertEquals("你好", context.getTranslatedTextMap().get(1));
            assertNull(context.getTranslatedTextMap().get(2));
            assertEquals("", context.getTranslatedTextMap().get(3));
            assertNotNull(context.getTranslatedTextMap().get(4));
        }
    }

    @Test
    @DisplayName("测试重复文本处理")
    void testTranslateWithDuplicateText() {
        // 准备数据 - 相同的文本出现多次
        originalTextMap.put(1, "Hello");
        originalTextMap.put(2, "Hello");
        originalTextMap.put(3, "World");

        // Mock 缓存返回 null
        when(redisProcessService.getCacheData("zh-CN", "Hello")).thenReturn(null);
        when(redisProcessService.getCacheData("zh-CN", "World")).thenReturn(null);

        // Mock AI 翻译 - 使用 Map<Integer, String> 类型避免方法歧义
        when(modelTranslateService.modelTranslate(eq("qwen-max"), anyString(), eq("zh-CN"), ArgumentMatchers.<Map<Integer, String>>any()))
                .thenReturn(new Pair<>("{\"1\":\"你好\",\"3\":\"世界\"}", 150));

        // Mock 静态方法 calculateBaiLianToken
        try (MockedStatic<ALiYunTranslateIntegration> mockedStatic = mockStatic(ALiYunTranslateIntegration.class)) {
            mockedStatic.when(() -> ALiYunTranslateIntegration.calculateBaiLianToken(anyString()))
                    .thenAnswer(invocation -> {
                        String text = invocation.getArgument(0);
                        return text != null ? text.length() : 0;
                    });

            // 执行
            batchTranslateStrategyService.translate(context);

            // 验证（必须在 mock 作用域内）- 相同的文本应该只翻译一次，但结果应该应用到所有位置
            String translatedHello = context.getTranslatedTextMap().get(1);
            String translatedHello2 = context.getTranslatedTextMap().get(2);
            assertEquals(translatedHello, translatedHello2); // 两个位置应该得到相同的翻译
            assertNotNull(context.getTranslatedTextMap().get(3));
        }
    }

    @Test
    @DisplayName("测试AI翻译返回null时的处理")
    void testTranslateWhenAITranslateReturnsNull() {
        // 准备数据
        originalTextMap.put(1, "Hello");

        // Mock 缓存返回 null
        when(redisProcessService.getCacheData("zh-CN", "Hello")).thenReturn(null);

        // Mock AI 翻译返回 null - 使用 Map<Integer, String> 类型避免方法歧义
        @SuppressWarnings("unchecked")
        Map<Integer, String> sourceMapType = any(Map.class);
        when(modelTranslateService.modelTranslate(eq("qwen-max"), anyString(), eq("zh-CN"), sourceMapType))
                .thenReturn(null);

        // Mock 静态方法 calculateBaiLianToken
        try (MockedStatic<ALiYunTranslateIntegration> mockedStatic = mockStatic(ALiYunTranslateIntegration.class)) {
            mockedStatic.when(() -> ALiYunTranslateIntegration.calculateBaiLianToken(anyString()))
                    .thenAnswer(invocation -> {
                        String text = invocation.getArgument(0);
                        return text != null ? text.length() : 0;
                    });

            // 执行
            batchTranslateStrategyService.translate(context);

            // 验证（必须在 mock 作用域内）- 当AI翻译返回null时，应该保持原文
            assertEquals("Hello", context.getTranslatedTextMap().get(1));
        }
    }

    @Test
    @DisplayName("测试混合场景：缓存+词汇表+AI")
    void testTranslateWithMixedScenarios() {
        // 准备数据
        originalTextMap.put(1, "Cached");      // 缓存命中
        originalTextMap.put(2, "Glossary");    // 词汇表匹配
        originalTextMap.put(3, "AI Translate"); // 需要AI翻译

        // 设置词汇表
        GlossaryDO glossary = new GlossaryDO("Glossary", "词汇表", 0);
        glossaryMap.put("Glossary", glossary);

        // Mock 缓存
        when(redisProcessService.getCacheData("zh-CN", "Cached")).thenReturn("已缓存");
        when(redisProcessService.getCacheData("zh-CN", "Glossary")).thenReturn(null);
        when(redisProcessService.getCacheData("zh-CN", "AI Translate")).thenReturn(null);

        // Mock AI 翻译 - 使用 Map<Integer, String> 类型避免方法歧义
        when(modelTranslateService.modelTranslate(eq("qwen-max"), anyString(), eq("zh-CN"), ArgumentMatchers.<Map<Integer, String>>any()))
                .thenReturn(new Pair<>("{\"3\":\"AI翻译\"}", 100));

        // Mock 静态方法 calculateBaiLianToken
        try (MockedStatic<ALiYunTranslateIntegration> mockedStatic = mockStatic(ALiYunTranslateIntegration.class)) {
            mockedStatic.when(() -> ALiYunTranslateIntegration.calculateBaiLianToken(anyString()))
                    .thenAnswer(invocation -> {
                        String text = invocation.getArgument(0);
                        return text != null ? text.length() : 0;
                    });

            // 执行
            batchTranslateStrategyService.translate(context);

            // 验证（必须在 mock 作用域内）
            assertEquals("已缓存", context.getTranslatedTextMap().get(1));
            assertEquals("词汇表", context.getTranslatedTextMap().get(2));
            assertNotNull(context.getTranslatedTextMap().get(3));
            assertEquals(1, context.getCachedCount());
            assertTrue(context.getGlossaryCount() > 0);
            assertTrue(context.getUsedToken() > 0);
        }
    }

    @Test
    @DisplayName("测试策略名称设置")
    void testStrategyNameSetting() {
        // 准备数据
        originalTextMap.put(1, "Hello");
        when(redisProcessService.getCacheData("zh-CN", "Hello")).thenReturn("你好");

        // 执行
        batchTranslateStrategyService.translate(context);

        // 验证
        assertEquals("Batch json 翻译", context.getStrategy());
    }

    @Test
    @DisplayName("测试Token计数")
    void testTokenCounting() {
        // 准备数据
        originalTextMap.put(1, "Hello");
        originalTextMap.put(2, "World");

        // Mock 缓存返回 null
        when(redisProcessService.getCacheData("zh-CN", "Hello")).thenReturn(null);
        when(redisProcessService.getCacheData("zh-CN", "World")).thenReturn(null);

        // Mock AI 翻译返回特定token数 - 使用 Map<Integer, String> 类型避免方法歧义
        @SuppressWarnings("unchecked")
        Map<Integer, String> sourceMapType = any(Map.class);
        int expectedTokens = 250;
        when(modelTranslateService.modelTranslate(eq("qwen-max"), anyString(), eq("zh-CN"), sourceMapType))
                .thenReturn(new Pair<>("{\"1\":\"你好\",\"2\":\"世界\"}", expectedTokens));

        // Mock 静态方法 calculateBaiLianToken
        try (MockedStatic<ALiYunTranslateIntegration> mockedStatic = mockStatic(ALiYunTranslateIntegration.class)) {
            mockedStatic.when(() -> ALiYunTranslateIntegration.calculateBaiLianToken(anyString()))
                    .thenAnswer(invocation -> {
                        String text = invocation.getArgument(0);
                        return text != null ? text.length() : 0;
                    });

            // 执行
            batchTranslateStrategyService.translate(context);

            // 验证（必须在 mock 作用域内）
            assertEquals(expectedTokens, context.getUsedToken());
        }
    }
}
