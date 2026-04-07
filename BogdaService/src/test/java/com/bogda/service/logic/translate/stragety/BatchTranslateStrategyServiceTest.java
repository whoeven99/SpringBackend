package com.bogda.service.logic.translate.stragety;

import com.bogda.common.TranslateContext;
import com.bogda.common.entity.DO.GlossaryDO;
import com.bogda.common.utils.JsonUtils;
import com.bogda.integration.feishu.FeiShuRobotIntegration;
import com.bogda.service.integration.ALiYunTranslateIntegration;
import com.bogda.service.logic.RedisProcessService;
import com.bogda.service.logic.redis.TranslateTaskMonitorV2RedisService;
import com.bogda.service.logic.translate.ModelTranslateService;
import com.bogda.service.logic.translate.PromptConfigService;
import kotlin.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
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

    @Mock
    private PromptConfigService promptConfigService;

    @Mock
    private FeiShuRobotIntegration feiShuRobotIntegration;

    @InjectMocks
    private BatchTranslateStrategyService batchTranslateStrategyService;

    private TranslateContext context;
    private String testTarget;
    private String testAiModel;
    private String testModule;
    private String testShopName;

    @BeforeEach
    void setUp() {
        testTarget = "zh";
        testAiModel = "kimi-k2.5";
        testModule = "product";
        testShopName = "unit-test-shop";

        Map<Integer, String> originalTextMap = new HashMap<>();
        originalTextMap.put(1, "Hello");
        originalTextMap.put(2, "World");

        context = new TranslateContext(originalTextMap, testTarget, new HashMap<>(), testAiModel);
        context.setModule(testModule);
        context.setShopName(testShopName);
    }

    private static Object invokePrivate(
            Object target,
            String methodName,
            Class<?>[] paramTypes,
            Object... args
    ) throws Exception {
        Method m = target.getClass().getDeclaredMethod(methodName, paramTypes);
        m.setAccessible(true);
        return m.invoke(target, args);
    }

    private static Object getField(Object obj, String fieldName) throws Exception {
        Field f = obj.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(obj);
    }

    @Test
    void testGetType_ShouldReturnBatch() {
        // When
        String type = batchTranslateStrategyService.getType();

        // Then
        assertEquals("BATCH", type);
    }

    @Test
    void testDeduplicateTexts_ShouldDeduplicateByFirstOccurrence_AndBuildMappingAndOccurrences() throws Exception {
        // Given: 注意 LinkedHashMap 保证遍历顺序
        Map<Integer, String> original = new LinkedHashMap<>();
        original.put(10, "A");
        original.put(11, "B");
        original.put(12, "A"); // duplicate
        original.put(13, "");  // empty -> ignored in dedup + mapping
        original.put(14, null); // null -> ignored in dedup + mapping
        original.put(15, "C");
        original.put(16, "B"); // duplicate

        // When
        Object dedupResult = invokePrivate(
                batchTranslateStrategyService,
                "deduplicateTexts",
                new Class<?>[]{Map.class},
                original
        );

        // Then
        @SuppressWarnings("unchecked")
        Map<Integer, String> actuallyTranslateMap = (Map<Integer, String>) getField(dedupResult, "actuallyTranslateMap");
        @SuppressWarnings("unchecked")
        Map<Integer, Integer> translateMappingMap = (Map<Integer, Integer>) getField(dedupResult, "translateMappingMap");
        @SuppressWarnings("unchecked")
        Map<String, Long> textOccurrences = (Map<String, Long>) getField(dedupResult, "textOccurrences");

        // seq 从 1 开始，且按首次出现顺序 A,B,C
        assertEquals(Map.of(1, "A", 2, "B", 3, "C"), actuallyTranslateMap);

        // mapping：只映射非空文本，且同文映射到同一 seq
        assertEquals(1, translateMappingMap.get(10));
        assertEquals(2, translateMappingMap.get(11));
        assertEquals(1, translateMappingMap.get(12));
        assertFalse(translateMappingMap.containsKey(13));
        assertFalse(translateMappingMap.containsKey(14));
        assertEquals(3, translateMappingMap.get(15));
        assertEquals(2, translateMappingMap.get(16));

        // occurrences：只统计非空，A=2,B=2,C=1
        assertEquals(2L, textOccurrences.get("A"));
        assertEquals(2L, textOccurrences.get("B"));
        assertEquals(1L, textOccurrences.get("C"));
        assertFalse(textOccurrences.containsKey(""));
    }

    @Test
    void testFillTranslatedResults_ShouldFillByMapping_PreserveNullAndEmpty_AndFallbackToOriginalWhenMissing() throws Exception {
        // Given
        Map<Integer, String> original = new LinkedHashMap<>();
        original.put(1, "Hello");
        original.put(2, "Hello"); // duplicate -> same seq
        original.put(3, "World"); // seq but missing translation -> fallback
        original.put(4, "");      // empty -> keep empty
        original.put(5, null);    // null -> keep null

        context = new TranslateContext(original, testTarget, new HashMap<>(), testAiModel);
        context.setModule(testModule);
        context.setShopName(testShopName);

        Map<Integer, Integer> mapping = new HashMap<>();
        mapping.put(1, 1);
        mapping.put(2, 1);
        mapping.put(3, 2);

        Map<Integer, String> translatedResultMap = new HashMap<>();
        translatedResultMap.put(1, "你好");
        // seq=2 缺失，模拟翻译失败/未返回

        // When
        invokePrivate(
                batchTranslateStrategyService,
                "fillTranslatedResults",
                new Class<?>[]{TranslateContext.class, Map.class, Map.class},
                context,
                mapping,
                translatedResultMap
        );

        // Then
        assertEquals("你好", context.getTranslatedTextMap().get(1));
        assertEquals("你好", context.getTranslatedTextMap().get(2));
        assertEquals("World", context.getTranslatedTextMap().get(3)); // fallback original
        assertEquals("", context.getTranslatedTextMap().get(4));
        assertNull(context.getTranslatedTextMap().get(5));
    }

    @Test
    void testExecuteBatchTranslation_WhenSuccess_ShouldCallPromptAndModelTranslate_AndFillTranslatedAndCacheAndToken() throws Exception {
        // Given
        Map<Integer, String> batchTexts = new LinkedHashMap<>();
        batchTexts.put(1, "Hello");
        batchTexts.put(2, "World");

        Map<Integer, String> actuallyTranslateMap = new HashMap<>();
        actuallyTranslateMap.put(1, "Hello");
        actuallyTranslateMap.put(2, "World");

        Map<Integer, String> translatedResultMap = new HashMap<>();

        when(promptConfigService.buildPlainJsonPrompt(eq(testModule), eq(testTarget), same(batchTexts)))
                .thenReturn("plain-prompt");
        when(modelTranslateService.modelTranslate(eq(testAiModel), eq("plain-prompt"), eq(testTarget), same(batchTexts)))
                .thenReturn(new Pair<>("{\"1\":\"你好\",\"2\":\"世界\"}", 77));

        // When
        invokePrivate(
                batchTranslateStrategyService,
                "executeBatchTranslation",
                new Class<?>[]{TranslateContext.class, Map.class, Map.class, Map.class},
                context,
                batchTexts,
                translatedResultMap,
                actuallyTranslateMap
        );

        // Then: 结果写入 translatedResultMap
        assertEquals("你好", translatedResultMap.get(1));
        assertEquals("世界", translatedResultMap.get(2));

        // Then: token 累计
        assertEquals(77, context.getUsedToken());

        // Then: 缓存写入（target, translation, originalText）
        verify(redisProcessService).setCacheData(testTarget, "你好", "Hello");
        verify(redisProcessService).setCacheData(testTarget, "世界", "World");

        verify(feiShuRobotIntegration, never()).sendMessage(contains("FatalException BATCH translateWithAI"));
    }

    @Test
    void testExecuteBatchTranslation_WhenBatchTranslateReturnsNull_ShouldSendFeishuAndNotMutateResults() throws Exception {
        // Given
        Map<Integer, String> batchTexts = new LinkedHashMap<>();
        batchTexts.put(1, "Hello");

        Map<Integer, String> actuallyTranslateMap = Map.of(1, "Hello");
        Map<Integer, String> translatedResultMap = new HashMap<>();

        when(promptConfigService.buildPlainJsonPrompt(eq(testModule), eq(testTarget), same(batchTexts)))
                .thenReturn("plain-prompt");
        when(modelTranslateService.modelTranslate(eq(testAiModel), eq("plain-prompt"), eq(testTarget), same(batchTexts)))
                .thenReturn(null);

        // When
        invokePrivate(
                batchTranslateStrategyService,
                "executeBatchTranslation",
                new Class<?>[]{TranslateContext.class, Map.class, Map.class, Map.class},
                context,
                batchTexts,
                translatedResultMap,
                actuallyTranslateMap
        );

        // Then
        assertTrue(translatedResultMap.isEmpty());
        assertEquals(0, context.getUsedToken());
        verify(redisProcessService, never()).setCacheData(anyString(), anyString(), anyString());
        // executeBatchTranslation() 当前实现对纯 AI 批次失败仅做 return，不发送飞书告警
        verify(feiShuRobotIntegration, never()).sendMessage(contains("FatalException BATCH translateWithAI"));
    }

    @Test
    void testTranslate_WithCachedValues_ShouldUseCache() {
        // Given
        when(redisProcessService.getCacheData(testTarget, "Hello")).thenReturn("你好");
        when(redisProcessService.getCacheData(testTarget, "World")).thenReturn("世界");
        context.setGlossaryMap(new HashMap<>());

        // When
        batchTranslateStrategyService.translate(context);

        // Then
        assertEquals("你好", context.getTranslatedTextMap().get(1));
        assertEquals("世界", context.getTranslatedTextMap().get(2));
        assertEquals(2, context.getCachedCount());
        verify(translateTaskMonitorV2RedisService).addCacheCount("Hello");
        verify(translateTaskMonitorV2RedisService).addCacheCount("World");
    }

    @Test
    void testTranslate_WithGlossaryMatch_ShouldUseGlossary_AndGlossaryPriorityOverCache() {
        // Given
        Map<String, GlossaryDO> glossaryMap = new HashMap<>();
        glossaryMap.put("Hello", new GlossaryDO("Hello", "你好", 0));

        context.setGlossaryMap(glossaryMap);
        // 如果 glossary 完全匹配，应该直接用词汇表结果，不走缓存
        when(redisProcessService.getCacheData(testTarget, "World")).thenReturn("世界");

        // When
        batchTranslateStrategyService.translate(context);

        // Then
        assertEquals("你好", context.getTranslatedTextMap().get(1));
        assertEquals("世界", context.getTranslatedTextMap().get(2));
        assertEquals(1, context.getGlossaryCount());
        verify(redisProcessService, never()).getCacheData(testTarget, "Hello");
    }

    @Test
    void testTranslate_WithUncachedText_ShouldCallModelTranslate() {
        // Given
        when(redisProcessService.getCacheData(anyString(), anyString())).thenReturn(null);
        context.setGlossaryMap(new HashMap<>());

        when(promptConfigService.buildPlainJsonPrompt(any(), eq(testTarget), anyMap())).thenReturn("batch-json-prompt");
        when(modelTranslateService.modelTranslate(eq(testAiModel), anyString(), eq(testTarget), anyMap()))
                .thenReturn(new Pair<>("{\"1\":\"你好\",\"2\":\"世界\"}", 100));

        try (MockedStatic<ALiYunTranslateIntegration> mockedAliyun = mockStatic(ALiYunTranslateIntegration.class)) {
            mockedAliyun.when(() -> ALiYunTranslateIntegration.calculateBaiLianToken(anyString())).thenReturn(10);

            // When
            batchTranslateStrategyService.translate(context);

            // Then
            assertEquals("你好", context.getTranslatedTextMap().get(1));
            assertEquals("世界", context.getTranslatedTextMap().get(2));
            assertEquals(100, context.getUsedToken());
            verify(modelTranslateService, atLeastOnce()).modelTranslate(eq(testAiModel), anyString(), eq(testTarget), anyMap());
            verify(redisProcessService, atLeastOnce()).setCacheData(eq(testTarget), anyString(), anyString());
        }
    }

    @Test
    void testTranslate_ShouldDeduplicate_KeepNullAndEmpty_AndFillBackInOriginalOrder() {
        // Given
        Map<Integer, String> original = new LinkedHashMap<>();
        original.put(1, "Hello");
        original.put(2, "Hello"); // duplicate
        original.put(3, "");      // empty
        original.put(4, null);    // null
        original.put(5, "World");

        context = new TranslateContext(original, testTarget, new HashMap<>(), testAiModel);
        context.setModule(testModule);
        context.setShopName(testShopName);

        when(redisProcessService.getCacheData(anyString(), anyString())).thenReturn(null);
        when(promptConfigService.buildPlainJsonPrompt(any(), eq(testTarget), anyMap())).thenReturn("batch-json-prompt");
        when(modelTranslateService.modelTranslate(eq(testAiModel), anyString(), eq(testTarget), anyMap()))
                .thenReturn(new Pair<>("{\"1\":\"你好\",\"2\":\"世界\"}", 50));

        try (MockedStatic<ALiYunTranslateIntegration> mockedAliyun = mockStatic(ALiYunTranslateIntegration.class)) {
            mockedAliyun.when(() -> ALiYunTranslateIntegration.calculateBaiLianToken(anyString())).thenReturn(10);

            // When
            batchTranslateStrategyService.translate(context);

            // Then: duplicate 回填成相同译文；空与null保持原样
            assertEquals("你好", context.getTranslatedTextMap().get(1));
            assertEquals("你好", context.getTranslatedTextMap().get(2));
            assertEquals("", context.getTranslatedTextMap().get(3));
            assertNull(context.getTranslatedTextMap().get(4));
            assertEquals("世界", context.getTranslatedTextMap().get(5));

            // 只需要翻译两条唯一文本 -> 至少一次 modelTranslate（可能一次批次）
            verify(modelTranslateService, times(1)).modelTranslate(eq(testAiModel), anyString(), eq(testTarget), anyMap());
        }
    }

    @Test
    void testTranslate_WithCacheHit_ShouldIncrementCountsByOccurrences() {
        // Given: Hello 出现 2 次，缓存命中应计数 2 次
        Map<Integer, String> original = new LinkedHashMap<>();
        original.put(1, "Hello");
        original.put(2, "Hello");
        original.put(3, "World");

        context = new TranslateContext(original, testTarget, new HashMap<>(), testAiModel);
        context.setModule(testModule);
        context.setShopName(testShopName);

        when(redisProcessService.getCacheData(testTarget, "Hello")).thenReturn("你好");
        when(redisProcessService.getCacheData(testTarget, "World")).thenReturn("世界");

        // When
        batchTranslateStrategyService.translate(context);

        // Then
        assertEquals("你好", context.getTranslatedTextMap().get(1));
        assertEquals("你好", context.getTranslatedTextMap().get(2));
        assertEquals("世界", context.getTranslatedTextMap().get(3));
        assertEquals(3, context.getCachedCount());
        verify(translateTaskMonitorV2RedisService, times(2)).addCacheCount("Hello");
        verify(translateTaskMonitorV2RedisService).addCacheCount("World");
    }

    @Test
    void testTranslate_WithGlossaryContained_ShouldTranslateWithGlossaryPrompt_AndCacheResult() {
        // Given: 文本包含术语 Apple（非完全匹配），应走“带词汇表批量翻译”路径
        Map<String, GlossaryDO> glossaryMap = new HashMap<>();
        glossaryMap.put("Apple", new GlossaryDO("Apple", "苹果", 0));

        Map<Integer, String> original = new LinkedHashMap<>();
        original.put(1, "I like Apple");

        context = new TranslateContext(original, testTarget, glossaryMap, testAiModel);
        context.setModule(testModule);
        context.setShopName(testShopName);

        when(promptConfigService.buildGlossaryJsonPrompt(eq(testModule), eq(testTarget), anyString(), anyMap()))
                .thenReturn("glossary-json-prompt");
        when(modelTranslateService.modelTranslate(eq(testAiModel), eq("glossary-json-prompt"), eq(testTarget), anyMap()))
                .thenReturn(new Pair<>("{\"1\":\"我喜欢苹果\"}", 12));

        // When
        batchTranslateStrategyService.translate(context);

        // Then
        assertEquals("我喜欢苹果", context.getTranslatedTextMap().get(1));
        assertEquals(12, context.getUsedToken());
        assertEquals(1, context.getGlossaryCount());
        verify(redisProcessService).setCacheData(testTarget, "我喜欢苹果", "I like Apple");
    }

    @Test
    void testTranslate_WithAI_ShouldSplitIntoBatchesByTokenLimit() {
        // Given: 3 条文本都需纯AI翻译；通过 mock token 计数触发 2 个批次
        Map<Integer, String> original = new LinkedHashMap<>();
        original.put(1, "t1");
        original.put(2, "t2");
        original.put(3, "t3");

        context = new TranslateContext(original, testTarget, new HashMap<>(), testAiModel);
        context.setModule(testModule);
        context.setShopName(testShopName);

        when(redisProcessService.getCacheData(anyString(), anyString())).thenReturn(null);

        // prompt 根据 map keys 变化，便于区分不同批次
        when(promptConfigService.buildPlainJsonPrompt(eq(testModule), eq(testTarget), anyMap()))
                .thenAnswer(invocation -> {
                    Map<Integer, String> map = invocation.getArgument(2);
                    List<Integer> keys = new ArrayList<>(map.keySet());
                    keys.sort(Integer::compareTo);
                    return "plain:" + keys;
                });

        // modelTranslate 按 prompt 返回对应的 JSON（直接用 seq -> 译文），并记录每次调用拿到的 batch（注意：生产代码会复用并 clear 同一个 Map 引用）
        List<Map<Integer, String>> capturedBatches = new ArrayList<>();
        when(modelTranslateService.modelTranslate(eq(testAiModel), anyString(), eq(testTarget), anyMap()))
                .thenAnswer(invocation -> {
                    Map<Integer, String> src = invocation.getArgument(3);
                    capturedBatches.add(new LinkedHashMap<>(src));
                    LinkedHashMap<Integer, String> out = new LinkedHashMap<>();
                    for (Map.Entry<Integer, String> e : src.entrySet()) {
                        out.put(e.getKey(), "tr_" + e.getValue());
                    }
                    return new Pair<>(JsonUtils.objectToJson(out), 3);
                });

        try (MockedStatic<ALiYunTranslateIntegration> mockedAliyun = mockStatic(ALiYunTranslateIntegration.class)) {
            mockedAliyun.when(() -> ALiYunTranslateIntegration.calculateBaiLianToken("t1")).thenReturn(400);
            mockedAliyun.when(() -> ALiYunTranslateIntegration.calculateBaiLianToken("t2")).thenReturn(250); // 触发阈值 650 >= 600
            mockedAliyun.when(() -> ALiYunTranslateIntegration.calculateBaiLianToken("t3")).thenReturn(100);

            // When
            batchTranslateStrategyService.translate(context);

            // Then
            verify(modelTranslateService, atLeastOnce()).modelTranslate(eq(testAiModel), anyString(), eq(testTarget), anyMap());
            verify(redisProcessService, atLeastOnce()).setCacheData(eq(testTarget), anyString(), anyString());
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

        // When
        LinkedHashMap<Integer, String> result = batchTranslateStrategyService.parseOutput(input);

        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("你好", result.get(1));
        assertEquals("世界", result.get(2));
    }

    @Test
    void testParseOutput_WithUnescapedQuotesInsideString_ShouldRepairAndReturnMap() {
        // Given: JSON 字符串值中包含未转义的双引号（AI 常见脏输出）
        String badJson = "{\"53\":\" (tenzij uitdrukkelijk door ons vermeld) \"zoals ze zijn\" en \"zoals beschikbaar\" aangeboden voor uw\"}";

        // When
        LinkedHashMap<Integer, String> result = batchTranslateStrategyService.parseOutput(badJson);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey(53));
        assertEquals(" (tenzij uitdrukkelijk door ons vermeld) \"zoals ze zijn\" en \"zoals beschikbaar\" aangeboden voor uw", result.get(53));
    }

    @Test
    void testParseOutput_WithNullInput_ShouldReturnNull() {
        // When
        LinkedHashMap<Integer, String> result = batchTranslateStrategyService.parseOutput(null);

        // Then
        assertNull(result);
    }

    @Test
    void testParseOutput_WithInvalidJson_ShouldReturnNull() {
        // Given
        String input = "invalid json";

        // When
        LinkedHashMap<Integer, String> result = batchTranslateStrategyService.parseOutput(input);

        // Then
        assertNull(result);
    }

    @Test
    void testParseOutput_WithEmptyValues_ShouldFilterEmptyValues() {
        // Given
        String input = "{\"1\":\"你好\",\"2\":\"\",\"3\":\"   \"}";

        // When
        LinkedHashMap<Integer, String> result = batchTranslateStrategyService.parseOutput(input);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey(1));
        assertEquals("你好", result.get(1));
    }

    @Test
    void testParseOutput_WithDoubleEscapedUnicodeQuotes_ShouldDecodeBeforeParse() {
        // Given: JSON 字符串值里包含字面量 \\u0022（两个反斜杠），需要在解析前还原成 \u0022
        String input = "{\"1\":\"Hello " + "\\\\" + "u0022world" + "\\\\" + "u0022\"}";

        // When
        LinkedHashMap<Integer, String> result = batchTranslateStrategyService.parseOutput(input);

        // Then: 还原后 Jackson 会把 \u0022 解码为真实双引号
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Hello \"world\"", result.get(1));
    }

    @Test
    void testApplyBatchResult_ShouldWriteOnlyRequestedSeq_AndCache_AndIncrementToken() throws Exception {
        // Given
        TranslateContext ctx = new TranslateContext(new HashMap<Integer, String>(), testTarget, new HashMap<>(), testAiModel);

        Map<Integer, String> requestedSourceMap = new LinkedHashMap<>();
        requestedSourceMap.put(1, "Hello");
        requestedSourceMap.put(2, "World");

        Map<Integer, String> actuallyTranslateMap = new HashMap<>();
        actuallyTranslateMap.put(1, "Hello");
        actuallyTranslateMap.put(2, "World");
        actuallyTranslateMap.put(999, "Unexpected");

        Map<Integer, String> translatedResultMap = new HashMap<>();

        // 模型“多吐”一个 999（不属于本批次 requestedSourceMap），应被忽略
        Map<Integer, String> out = new LinkedHashMap<>();
        out.put(1, "你好");
        out.put(2, "世界");
        out.put(999, "不应写入");

        Pair<Map<Integer, String>, Integer> result = new Pair<>(out, 12);

        // When
        invokePrivate(
                batchTranslateStrategyService,
                "applyBatchResult",
                new Class<?>[]{TranslateContext.class, Pair.class, Map.class, Map.class, Map.class, String.class},
                ctx,
                result,
                requestedSourceMap,
                actuallyTranslateMap,
                translatedResultMap,
                testTarget
        );

        // Then: token 累计
        assertEquals(12, ctx.getUsedToken());

        // Then: 仅写入 requested 的 seq
        assertEquals("你好", translatedResultMap.get(1));
        assertEquals("世界", translatedResultMap.get(2));
        assertFalse(translatedResultMap.containsKey(999));

        // Then: 仅对合法 seq 写缓存
        verify(redisProcessService).setCacheData(testTarget, "你好", "Hello");
        verify(redisProcessService).setCacheData(testTarget, "世界", "World");
        verify(redisProcessService, never()).setCacheData(eq(testTarget), eq("不应写入"), anyString());
    }

    @Test
    void testApplyBatchResult_WhenAllSeqUnexpected_ShouldOnlyIncrementToken_AndNotWriteAnything() throws Exception {
        // Given
        TranslateContext ctx = new TranslateContext(new HashMap<Integer, String>(), testTarget, new HashMap<>(), testAiModel);

        Map<Integer, String> requestedSourceMap = new LinkedHashMap<>();
        requestedSourceMap.put(1, "Hello");

        Map<Integer, String> actuallyTranslateMap = new HashMap<>();
        actuallyTranslateMap.put(1, "Hello");
        actuallyTranslateMap.put(2, "World");

        Map<Integer, String> translatedResultMap = new HashMap<>();

        Map<Integer, String> out = new LinkedHashMap<>();
        out.put(2, "世界"); // 不属于 requestedSourceMap
        Pair<Map<Integer, String>, Integer> result = new Pair<>(out, 5);

        // When
        invokePrivate(
                batchTranslateStrategyService,
                "applyBatchResult",
                new Class<?>[]{TranslateContext.class, Pair.class, Map.class, Map.class, Map.class, String.class},
                ctx,
                result,
                requestedSourceMap,
                actuallyTranslateMap,
                translatedResultMap,
                testTarget
        );

        // Then
        assertEquals(5, ctx.getUsedToken());
        assertTrue(translatedResultMap.isEmpty());
        verify(redisProcessService, never()).setCacheData(anyString(), anyString(), anyString());
    }

    @Test
    void testBatchTranslate_WhenParseEmpty_ShouldRetryOnceAndReturnParsedResult() throws Exception {
        // Given
        BatchTranslateStrategyService spyService = spy(batchTranslateStrategyService);

        Map<Integer, String> sourceMap = new LinkedHashMap<>();
        sourceMap.put(1, "Hello");

        when(modelTranslateService.modelTranslate(eq(testAiModel), eq("p"), eq(testTarget), same(sourceMap)))
                .thenReturn(new Pair<>("raw1", 7))
                .thenReturn(new Pair<>("raw2", 9));

        // 第一次解析为空，触发重试；第二次解析成功
        doReturn(new LinkedHashMap<Integer, String>())
                .doReturn(new LinkedHashMap<Integer, String>() {{
                    put(1, "你好");
                }})
                .when(spyService).parseOutput(anyString());

        // When
        @SuppressWarnings("unchecked")
        Pair<Map<Integer, String>, Integer> result = (Pair<Map<Integer, String>, Integer>) invokePrivate(
                spyService,
                "batchTranslate",
                new Class<?>[]{String.class, String.class, String.class, Map.class},
                "p",
                testTarget,
                testAiModel,
                sourceMap
        );

        // Then
        assertNotNull(result);
        assertEquals(9, result.getSecond()); // token 取最后一次调用的
        assertEquals("你好", result.getFirst().get(1));
        verify(modelTranslateService, times(2)).modelTranslate(eq(testAiModel), eq("p"), eq(testTarget), same(sourceMap));
    }

    @Test
    void testBatchTranslate_WhenParseEmptyTwice_ShouldReturnNull() throws Exception {
        // Given
        BatchTranslateStrategyService spyService = spy(batchTranslateStrategyService);

        Map<Integer, String> sourceMap = new LinkedHashMap<>();
        sourceMap.put(1, "Hello");

        when(modelTranslateService.modelTranslate(eq(testAiModel), eq("p"), eq(testTarget), same(sourceMap)))
                .thenReturn(new Pair<>("raw1", 7))
                .thenReturn(new Pair<>("raw2", 9));

        // 两次解析都为空/空map -> 返回 null
        doReturn(new LinkedHashMap<Integer, String>())
                .doReturn(new LinkedHashMap<Integer, String>())
                .when(spyService).parseOutput(anyString());

        // When
        @SuppressWarnings("unchecked")
        Pair<Map<Integer, String>, Integer> result = (Pair<Map<Integer, String>, Integer>) invokePrivate(
                spyService,
                "batchTranslate",
                new Class<?>[]{String.class, String.class, String.class, Map.class},
                "p",
                testTarget,
                testAiModel,
                sourceMap
        );

        // Then
        assertNull(result);
        verify(modelTranslateService, times(2)).modelTranslate(eq(testAiModel), eq("p"), eq(testTarget), same(sourceMap));
    }

    @Test
    void testBatchTranslate_WhenModelTranslateNull_ShouldReturnNull() throws Exception {
        // Given
        Map<Integer, String> sourceMap = new LinkedHashMap<>();
        sourceMap.put(1, "Hello");

        when(modelTranslateService.modelTranslate(eq(testAiModel), eq("p"), eq(testTarget), same(sourceMap)))
                .thenReturn(null);

        // When
        @SuppressWarnings("unchecked")
        Pair<Map<Integer, String>, Integer> result = (Pair<Map<Integer, String>, Integer>) invokePrivate(
                batchTranslateStrategyService,
                "batchTranslate",
                new Class<?>[]{String.class, String.class, String.class, Map.class},
                "p",
                testTarget,
                testAiModel,
                sourceMap
        );

        // Then
        assertNull(result);
        verify(modelTranslateService, times(1)).modelTranslate(eq(testAiModel), eq("p"), eq(testTarget), same(sourceMap));
    }
}

