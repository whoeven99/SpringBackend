package com.bogda.service.logic.translate.stragety;

import com.bogda.common.TranslateContext;
import com.bogda.common.utils.JsonUtils;
import com.bogda.service.logic.redis.ConfigRedisRepo;
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
class JsonTranslateStrategyServiceTest {

    @Mock
    private BatchTranslateStrategyService batchTranslateStrategyService;
    @Mock
    private ConfigRedisRepo configRedisRepo;

    @InjectMocks
    private JsonTranslateStrategyService jsonTranslateStrategyService;

    private TranslateContext context;
    private String testTarget;
    private String testAiModel;

    @BeforeEach
    void setUp() {
        testTarget = "zh";
        testAiModel = "gemini-3-flash";
    }

    @Test
    void testGetType_ShouldReturnJson() {
        // When
        String type = jsonTranslateStrategyService.getType();

        // Then
        assertEquals("JSON", type);
    }

    @Test
    void testTranslate_WithValidJson_ShouldTranslateTextNodes() {
        // Given
        String jsonContent = "{\"type\":\"text\",\"value\":\"Hello World\",\"children\":[]}";
        context = new TranslateContext(jsonContent, testTarget, new HashMap<>(), testAiModel);
        doAnswer(invocation -> {
            TranslateContext ctx = invocation.getArgument(0);
            Map<Integer, String> translatedMap = new HashMap<>();
            translatedMap.put(0, "你好世界");
            ctx.getTranslatedTextMap().putAll(translatedMap);
            return null;
        }).when(batchTranslateStrategyService).translate(context);

        // When
        jsonTranslateStrategyService.translate(context);

        // Then
        assertEquals("JSON翻译", context.getStrategy());
        assertNotNull(context.getTranslatedContent());
        assertTrue(context.getTranslatedContent().contains("\"value\":\"你好世界\""));
        verify(batchTranslateStrategyService).translate(context);
    }

    @Test
    void testTranslate_WithInvalidJson_ShouldReturnOriginalContent() {
        // Given
        String invalidJson = "invalid json";
        context = new TranslateContext(invalidJson, testTarget, new HashMap<>(), testAiModel);

        try (MockedStatic<JsonUtils> mockedJsonUtils = mockStatic(JsonUtils.class)) {
            mockedJsonUtils.when(() -> JsonUtils.readTree(invalidJson)).thenReturn(null);

            // When
            jsonTranslateStrategyService.translate(context);

            // Then
            assertEquals(invalidJson, context.getTranslatedContent());
            verifyNoInteractions(batchTranslateStrategyService);
        }
    }

    @Test
    void testTranslate_WithEmptyTextNodes_ShouldReturnOriginalContent() {
        // Given
        String jsonContent = "{\"type\":\"text\",\"value\":\"\",\"children\":[]}";
        context = new TranslateContext(jsonContent, testTarget, new HashMap<>(), testAiModel);
        // When
        jsonTranslateStrategyService.translate(context);

        // Then
        assertEquals("JSON翻译（无内容）", context.getStrategy());
        assertEquals(jsonContent, context.getTranslatedContent());
        verifyNoInteractions(batchTranslateStrategyService);
    }

    @Test
    void testTranslate_WithNestedJson_ShouldTranslateAllTextNodes() {
        // Given
        String jsonContent = "{\"type\":\"text\",\"value\":\"Hello\",\"children\":[{\"type\":\"text\",\"value\":\"World\"}]}";
        context = new TranslateContext(jsonContent, testTarget, new HashMap<>(), testAiModel);
        doAnswer(invocation -> {
            TranslateContext ctx = invocation.getArgument(0);
            Map<Integer, String> translatedMap = new HashMap<>();
            translatedMap.put(0, "你好");
            translatedMap.put(1, "世界");
            ctx.getTranslatedTextMap().putAll(translatedMap);
            return null;
        }).when(batchTranslateStrategyService).translate(context);

        // When
        jsonTranslateStrategyService.translate(context);

        // Then
        assertEquals("JSON翻译", context.getStrategy());
        assertTrue(context.getTranslatedContent().contains("\"value\":\"你好\""));
        assertTrue(context.getTranslatedContent().contains("\"value\":\"世界\""));
        verify(batchTranslateStrategyService).translate(context);
    }

    @Test
    void testFinishAndGetJsonRecord_ShouldSetVariables() {
        // Given
        context = new TranslateContext("{}", testTarget, new HashMap<>(), testAiModel);
        context.setStrategy("JSON翻译");
        context.setUsedToken(100);
        context.setCachedCount(0);
        context.setGlossaryCount(0);
        context.setTranslatedChars(50);
        context.setStartTime(System.currentTimeMillis() - 1000);

        // When
        jsonTranslateStrategyService.finishAndGetJsonRecord(context);

        // Then
        assertNotNull(context.getTranslateVariables());
        assertEquals("JSON翻译", context.getTranslateVariables().get("strategy"));
        assertEquals("100", context.getTranslateVariables().get("usedToken"));
        assertEquals("0", context.getTranslateVariables().get("cachedCount"));
        assertEquals("0", context.getTranslateVariables().get("glossaryCount"));
        assertEquals("50", context.getTranslateVariables().get("translatedChars"));
        assertNotNull(context.getTranslatedTime());
    }

    @Test
    void testTranslate_WithDynamicPathRules_ShouldTranslateVirtualOptions() {
        // Given
        String jsonContent = "{\"product_id\":\"6948995727533\",\"virtual_options\":[{\"title\":\"Entrer title\",\"values\":[{\"key\":\"Nous key\"}]}]}";
        String config = "{\"jsonExtractRules\":[{\"mode\":\"path\",\"path\":\"virtual_options[*].title\"},{\"mode\":\"path\",\"path\":\"virtual_options[*].values[*].key\"}]}";
        when(configRedisRepo.getConfig("METAFIELD_JSON_TRANSLATE_RULE")).thenReturn(config);
        context = new TranslateContext(jsonContent, testTarget, new HashMap<>(), testAiModel);

        doAnswer(invocation -> {
            TranslateContext ctx = invocation.getArgument(0);
            ctx.getOriginalTextMap().forEach((k, v) -> {
                if ("Entrer title".equals(v)) {
                    ctx.getTranslatedTextMap().put(k, "输入标题");
                } else if ("Nous key".equals(v)) {
                    ctx.getTranslatedTextMap().put(k, "联系说明");
                }
            });
            return null;
        }).when(batchTranslateStrategyService).translate(any(TranslateContext.class));

        // When
        jsonTranslateStrategyService.translate(context);

        // Then
        assertEquals("JSON翻译", context.getStrategy());
        assertTrue(context.getTranslatedContent().contains("\"title\":\"输入标题\""));
        assertTrue(context.getTranslatedContent().contains("\"key\":\"联系说明\""));
        verify(batchTranslateStrategyService).translate(context);
    }
}

