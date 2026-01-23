package com.bogda.service.logic.translate;

import com.bogda.integration.aimodel.ChatGptIntegration;
import com.bogda.integration.aimodel.GoogleMachineIntegration;
import kotlin.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TranslateGatewayTest {

    @Mock
    private GoogleMachineIntegration googleMachineIntegration;

    @Mock
    private ChatGptIntegration chatGptIntegration;

    @InjectMocks
    private TranslateGateway translateGateway;

    private String testPrompt;
    private String testTargetLanguage;

    @BeforeEach
    void setUp() {
        testPrompt = "Hello, world!";
        testTargetLanguage = "zh";
    }

    @Test
    void testTranslate_WithNullPrivateKey_ShouldReturnNull() {
        // When
        Pair<String, Integer> result = translateGateway.translate(testPrompt, testTargetLanguage, null, null);

        // Then
        assertNull(result);
        verifyNoInteractions(googleMachineIntegration);
        verifyNoInteractions(chatGptIntegration);
    }

    @Test
    void testTranslate_WithPrivateKeyAndModelFlag0_ShouldReturnNull() {
        // Given
        String privateKey = "test-key";
        Integer translateModelFlag = 0;

        // When
        Pair<String, Integer> result = translateGateway.translate(testPrompt, testTargetLanguage, privateKey, translateModelFlag);

        // Then
        assertNull(result);
        // Google integration is not implemented yet, so no verification
        verifyNoInteractions(chatGptIntegration);
    }

    @Test
    void testTranslate_WithPrivateKeyAndNonZeroModelFlag_ShouldReturnNull() {
        // Given
        String privateKey = "test-key";
        Integer translateModelFlag = 1;

        // When
        Pair<String, Integer> result = translateGateway.translate(testPrompt, testTargetLanguage, privateKey, translateModelFlag);

        // Then
        assertNull(result);
        // ChatGPT integration is commented out, so no verification
    }

    @Test
    void testTranslate_WithoutPrivateKey_ShouldReturnNull() {
        // When
        Pair<String, Integer> result = translateGateway.translate(testPrompt, testTargetLanguage);

        // Then
        assertNull(result);
        verifyNoInteractions(googleMachineIntegration);
        verifyNoInteractions(chatGptIntegration);
    }

    @Test
    void testTranslate_WithEmptyPrompt_ShouldReturnNull() {
        // Given
        String emptyPrompt = "";

        // When
        Pair<String, Integer> result = translateGateway.translate(emptyPrompt, testTargetLanguage);

        // Then
        assertNull(result);
    }

    @Test
    void testTranslate_WithNullPrompt_ShouldReturnNull() {
        // When
        Pair<String, Integer> result = translateGateway.translate(null, testTargetLanguage);

        // Then
        assertNull(result);
    }
}

