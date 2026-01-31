package com.bogda.service.logic.translate;

import com.bogda.common.utils.ModuleCodeUtils;
import com.bogda.integration.aimodel.ChatGptIntegration;
import com.bogda.integration.aimodel.GeminiIntegration;
import com.bogda.integration.aimodel.GoogleMachineIntegration;
import com.bogda.service.integration.ALiYunTranslateIntegration;
import kotlin.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModelTranslateServiceTest {

    @Mock
    private ALiYunTranslateIntegration aLiYunTranslateIntegration;

    @Mock
    private GeminiIntegration geminiIntegration;

    @Mock
    private ChatGptIntegration chatGptIntegration;

    @Mock
    private GoogleMachineIntegration googleMachineIntegration;

    @InjectMocks
    private ModelTranslateService modelTranslateService;

    private String testPrompt;
    private String testTarget;
    private String testSourceText;

    @BeforeEach
    void setUp() {
        testPrompt = "Translate this text";
        testTarget = "zh";
        testSourceText = "Hello, world!";
    }

    @Test
    void testAiTranslate_WithQwenMax_ShouldCallAliYunIntegration() {
        // Given
        String aiModel = ALiYunTranslateIntegration.QWEN_MAX;
        Pair<String, Integer> expectedPair = new Pair<>("翻译结果", 100);
        when(aLiYunTranslateIntegration.userTranslate(testPrompt, testTarget)).thenReturn(expectedPair);

        // When
        Pair<String, Integer> result = modelTranslateService.aiTranslate(aiModel, testPrompt, testTarget);

        // Then
        assertNotNull(result);
        assertEquals(expectedPair, result);
        verify(aLiYunTranslateIntegration).userTranslate(testPrompt, testTarget);
        verifyNoInteractions(geminiIntegration, chatGptIntegration);
    }

    @Test
    void testAiTranslate_WithGeminiFlash_ShouldCallGeminiIntegration() {
        // Given
        String aiModel = GeminiIntegration.GEMINI_3_FLASH;
        Pair<String, Integer> expectedPair = new Pair<>("翻译结果", 100);
        when(geminiIntegration.generateText(aiModel, testPrompt)).thenReturn(expectedPair);

        // When
        Pair<String, Integer> result = modelTranslateService.aiTranslate(aiModel, testPrompt, testTarget);

        // Then
        assertNotNull(result);
        assertEquals(expectedPair, result);
        verify(geminiIntegration).generateText(aiModel, testPrompt);
        verifyNoInteractions(aLiYunTranslateIntegration, chatGptIntegration);
    }

    @Test
    void testAiTranslate_WithGpt4_ShouldCallChatGptIntegration() {
        // Given
        String aiModel = ModuleCodeUtils.GPT_5;
        Pair<String, Integer> expectedPair = new Pair<>("翻译结果", 100);
        when(chatGptIntegration.chatWithGpt(testPrompt, testTarget)).thenReturn(expectedPair);

        // When
        Pair<String, Integer> result = modelTranslateService.aiTranslate(aiModel, testPrompt, testTarget);

        // Then
        assertNotNull(result);
        assertEquals(expectedPair, result);
        verify(chatGptIntegration).chatWithGpt(testPrompt, testTarget);
        verifyNoInteractions(aLiYunTranslateIntegration, geminiIntegration);
    }

    @Test
    void testAiTranslate_WithUnknownModel_ShouldReturnNull() {
        // Given
        String aiModel = "unknown-model";

        // When
        Pair<String, Integer> result = modelTranslateService.aiTranslate(aiModel, testPrompt, testTarget);

        // Then
        assertNull(result);
        verifyNoInteractions(aLiYunTranslateIntegration, geminiIntegration, chatGptIntegration);
    }

    @Test
    void testModelTranslate_WithStringSource_WithSuccessfulAiTranslate_ShouldReturnAiResult() {
        // Given
        String aiModel = ALiYunTranslateIntegration.QWEN_MAX;
        Pair<String, Integer> expectedPair = new Pair<>("翻译结果", 100);
        when(aLiYunTranslateIntegration.userTranslate(testPrompt, testTarget)).thenReturn(expectedPair);

        // When - explicitly call the String version
        Pair<String, Integer> result = modelTranslateService.modelTranslate(aiModel, testPrompt, testTarget, testSourceText);

        // Then
        assertNotNull(result);
        assertEquals(expectedPair, result);
        verify(aLiYunTranslateIntegration).userTranslate(testPrompt, testTarget);
        verifyNoInteractions(googleMachineIntegration);
    }

    @Test
    void testModelTranslate_WithStringSource_WithFailedAiTranslate_ShouldFallbackToGoogle() {
        // Given
        String aiModel = ALiYunTranslateIntegration.QWEN_MAX;
        Pair<String, Integer> googlePair = new Pair<>("Google翻译结果", 50);
        when(aLiYunTranslateIntegration.userTranslate(testPrompt, testTarget)).thenReturn(null);
        when(googleMachineIntegration.googleTranslateWithSDK(testSourceText, testTarget)).thenReturn(googlePair);

        // When - explicitly call the String version
        Pair<String, Integer> result = modelTranslateService.modelTranslate(aiModel, testPrompt, testTarget, testSourceText);

        // Then
        assertNotNull(result);
        assertEquals(googlePair, result);
        verify(aLiYunTranslateIntegration).userTranslate(testPrompt, testTarget);
        verify(googleMachineIntegration).googleTranslateWithSDK(testSourceText, testTarget);
    }

    @Test
    void testModelTranslate_WithMap_WithSuccessfulAiTranslate_ShouldReturnAiResult() {
        // Given
        String aiModel = ALiYunTranslateIntegration.QWEN_MAX;
        Map<Integer, String> sourceMap = new LinkedHashMap<>();
        sourceMap.put(1, "Hello");
        sourceMap.put(2, "World");
        Pair<String, Integer> expectedPair = new Pair<>("{\"1\":\"你好\",\"2\":\"世界\"}", 100);
        when(aLiYunTranslateIntegration.userTranslate(testPrompt, testTarget)).thenReturn(expectedPair);

        // When
        Pair<String, Integer> result = modelTranslateService.modelTranslate(aiModel, testPrompt, testTarget, sourceMap);

        // Then
        assertNotNull(result);
        assertEquals(expectedPair, result);
        verify(aLiYunTranslateIntegration).userTranslate(testPrompt, testTarget);
        verifyNoInteractions(googleMachineIntegration);
    }

    @Test
    void testModelTranslate_WithMap_WithFailedAiTranslate_ShouldFallbackToGoogle() {
        // Given
        String aiModel = ALiYunTranslateIntegration.QWEN_MAX;
        Map<Integer, String> sourceMap = new LinkedHashMap<>();
        sourceMap.put(1, "Hello");
        sourceMap.put(2, "World");
        when(aLiYunTranslateIntegration.userTranslate(testPrompt, testTarget)).thenReturn(null);
        when(googleMachineIntegration.googleTranslateWithSDK("Hello", testTarget)).thenReturn(new Pair<>("你好", 10));
        when(googleMachineIntegration.googleTranslateWithSDK("World", testTarget)).thenReturn(new Pair<>("世界", 10));

        // When
        Pair<String, Integer> result = modelTranslateService.modelTranslate(aiModel, testPrompt, testTarget, sourceMap);

        // Then
        assertNotNull(result);
        assertNotNull(result.getFirst());
        assertEquals(20, result.getSecond());
        verify(aLiYunTranslateIntegration).userTranslate(testPrompt, testTarget);
        verify(googleMachineIntegration, times(2)).googleTranslateWithSDK(anyString(), eq(testTarget));
    }

    @Test
    void testModelTranslate_WithMap_WithNullMap_ShouldReturnNull() {
        // Given
        String aiModel = ALiYunTranslateIntegration.QWEN_MAX;
        when(aLiYunTranslateIntegration.userTranslate(testPrompt, testTarget)).thenReturn(null);

        // When - explicitly pass null as Map to call the Map version
        Map<Integer, String> nullMap = null;
        Pair<String, Integer> result = modelTranslateService.modelTranslate(aiModel, testPrompt, testTarget, nullMap);

        // Then
        assertNull(result);
        verify(aLiYunTranslateIntegration).userTranslate(testPrompt, testTarget);
        verifyNoInteractions(googleMachineIntegration);
    }

    @Test
    void testModelTranslate_WithMap_WithEmptyMap_ShouldReturnNull() {
        // Given
        String aiModel = ALiYunTranslateIntegration.QWEN_MAX;
        Map<Integer, String> emptyMap = new LinkedHashMap<>();
        when(aLiYunTranslateIntegration.userTranslate(testPrompt, testTarget)).thenReturn(null);

        // When
        Pair<String, Integer> result = modelTranslateService.modelTranslate(aiModel, testPrompt, testTarget, emptyMap);

        // Then
        assertNull(result);
        verify(aLiYunTranslateIntegration).userTranslate(testPrompt, testTarget);
        verifyNoInteractions(googleMachineIntegration);
    }

    @Test
    void testModelTranslate_WithMap_WithBlankValues_ShouldSkipBlankValues() {
        // Given
        String aiModel = ALiYunTranslateIntegration.QWEN_MAX;
        Map<Integer, String> sourceMap = new LinkedHashMap<>();
        sourceMap.put(1, "Hello");
        sourceMap.put(2, "");
        sourceMap.put(3, "   ");
        when(aLiYunTranslateIntegration.userTranslate(testPrompt, testTarget)).thenReturn(null);
        when(googleMachineIntegration.googleTranslateWithSDK("Hello", testTarget)).thenReturn(new Pair<>("你好", 10));

        // When
        Pair<String, Integer> result = modelTranslateService.modelTranslate(aiModel, testPrompt, testTarget, sourceMap);

        // Then
        assertNotNull(result);
        verify(googleMachineIntegration, times(1)).googleTranslateWithSDK(anyString(), eq(testTarget));
    }

    @Test
    void testModelTranslate_WithMap_WithGoogleTranslateException_ShouldHandleGracefully() {
        // Given
        String aiModel = ALiYunTranslateIntegration.QWEN_MAX;
        Map<Integer, String> sourceMap = new LinkedHashMap<>();
        sourceMap.put(1, "Hello");
        when(aLiYunTranslateIntegration.userTranslate(testPrompt, testTarget)).thenReturn(null);
        when(googleMachineIntegration.googleTranslateWithSDK("Hello", testTarget))
                .thenThrow(new RuntimeException("Translation failed"));

        // When
        Pair<String, Integer> result = modelTranslateService.modelTranslate(aiModel, testPrompt, testTarget, sourceMap);

        // Then
        assertNotNull(result);
        // Should return original value when exception occurs
        assertTrue(result.getFirst().contains("Hello"));
        verify(googleMachineIntegration).googleTranslateWithSDK("Hello", testTarget);
    }
}

