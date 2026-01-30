package com.bogda.service.logic.translate.stragety;

import com.bogda.common.TranslateContext;
import com.bogda.common.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
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

        try (MockedStatic<JsonUtils> mockedJsonUtils = mockStatic(JsonUtils.class)) {
            JsonNode mockNode = mock(JsonNode.class);
            mockedJsonUtils.when(() -> JsonUtils.readTree(jsonContent)).thenReturn(mockNode);
            mockedJsonUtils.when(() -> JsonUtils.objectToJson(any())).thenReturn("{\"type\":\"text\",\"value\":\"你好世界\",\"children\":[]}");

            when(mockNode.isObject()).thenReturn(true);
            when(mockNode.has("type")).thenReturn(true);
            when(mockNode.get("type")).thenReturn(mock(JsonNode.class));
            when(mockNode.get("type").asText()).thenReturn("text");
            when(mockNode.has("value")).thenReturn(true);
            when(mockNode.get("value")).thenReturn(mock(JsonNode.class));
            when(mockNode.get("value").isTextual()).thenReturn(true);
            when(mockNode.get("value").asText()).thenReturn("Hello World");
            when(mockNode.has("children")).thenReturn(true);
            when(mockNode.get("children")).thenReturn(mock(JsonNode.class));
            when(mockNode.get("children").isArray()).thenReturn(true);
            when(mockNode.get("children").size()).thenReturn(0);

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
            verify(batchTranslateStrategyService).translate(context);
        }
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

        try (MockedStatic<JsonUtils> mockedJsonUtils = mockStatic(JsonUtils.class)) {
            JsonNode mockNode = mock(JsonNode.class);
            mockedJsonUtils.when(() -> JsonUtils.readTree(jsonContent)).thenReturn(mockNode);

            when(mockNode.isObject()).thenReturn(true);
            when(mockNode.has("type")).thenReturn(true);
            when(mockNode.get("type")).thenReturn(mock(JsonNode.class));
            when(mockNode.get("type").asText()).thenReturn("text");
            when(mockNode.has("value")).thenReturn(true);
            when(mockNode.get("value")).thenReturn(mock(JsonNode.class));
            when(mockNode.get("value").isTextual()).thenReturn(true);
            when(mockNode.get("value").asText()).thenReturn("");
            when(mockNode.has("children")).thenReturn(true);
            when(mockNode.get("children")).thenReturn(mock(JsonNode.class));
            when(mockNode.get("children").isArray()).thenReturn(true);
            when(mockNode.get("children").size()).thenReturn(0);

            // When
            jsonTranslateStrategyService.translate(context);

            // Then
            assertEquals("JSON翻译（无内容）", context.getStrategy());
            assertEquals(jsonContent, context.getTranslatedContent());
            verifyNoInteractions(batchTranslateStrategyService);
        }
    }

    @Test
    void testTranslate_WithNestedJson_ShouldTranslateAllTextNodes() {
        // Given
        String jsonContent = "{\"type\":\"text\",\"value\":\"Hello\",\"children\":[{\"type\":\"text\",\"value\":\"World\"}]}";
        context = new TranslateContext(jsonContent, testTarget, new HashMap<>(), testAiModel);

        try (MockedStatic<JsonUtils> mockedJsonUtils = mockStatic(JsonUtils.class)) {
            JsonNode rootNode = mock(JsonNode.class);
            JsonNode childNode = mock(JsonNode.class);
            JsonNode childrenArray = mock(JsonNode.class);

            mockedJsonUtils.when(() -> JsonUtils.readTree(jsonContent)).thenReturn(rootNode);
            mockedJsonUtils.when(() -> JsonUtils.objectToJson(any())).thenReturn(jsonContent);

            // Root node setup
            when(rootNode.isObject()).thenReturn(true);
            when(rootNode.has("type")).thenReturn(true);
            when(rootNode.get("type")).thenReturn(mock(JsonNode.class));
            when(rootNode.get("type").asText()).thenReturn("text");
            when(rootNode.has("value")).thenReturn(true);
            when(rootNode.get("value")).thenReturn(mock(JsonNode.class));
            when(rootNode.get("value").isTextual()).thenReturn(true);
            when(rootNode.get("value").asText()).thenReturn("Hello");
            when(rootNode.has("children")).thenReturn(true);
            when(rootNode.get("children")).thenReturn(childrenArray);
            when(childrenArray.isArray()).thenReturn(true);
            when(childrenArray.size()).thenReturn(1);
            when(childrenArray.get(0)).thenReturn(childNode);

            // Child node setup
            when(childNode.isObject()).thenReturn(true);
            when(childNode.has("type")).thenReturn(true);
            when(childNode.get("type")).thenReturn(mock(JsonNode.class));
            when(childNode.get("type").asText()).thenReturn("text");
            when(childNode.has("value")).thenReturn(true);
            when(childNode.get("value")).thenReturn(mock(JsonNode.class));
            when(childNode.get("value").isTextual()).thenReturn(true);
            when(childNode.get("value").asText()).thenReturn("World");
            when(childNode.has("children")).thenReturn(true);
            when(childNode.get("children")).thenReturn(mock(JsonNode.class));
            when(childNode.get("children").isArray()).thenReturn(true);
            when(childNode.get("children").size()).thenReturn(0);

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
            verify(batchTranslateStrategyService).translate(context);
        }
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
}

