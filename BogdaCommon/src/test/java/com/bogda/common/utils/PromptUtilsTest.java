package com.bogda.common.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

public class PromptUtilsTest {

    @Test
    void testGlossaryJsonPrompt() {
        String target = "en";
        String glossaryMapping = "term1:translation1\nterm2:translation2";
        Map<Integer, String> glossaryTextMap = new HashMap<>();
        glossaryTextMap.put(1, "text1");
        glossaryTextMap.put(2, "text2");

        String prompt = PromptUtils.GlossaryJsonPrompt(target, glossaryMapping, glossaryTextMap);
        assertNotNull(prompt);
        assertTrue(prompt.contains("English"));
        assertTrue(prompt.contains(glossaryMapping));
    }

    @Test
    void testGlossaryJsonPromptWithEmptyMap() {
        String target = "zh-CN";
        String glossaryMapping = "term:translation";
        Map<Integer, String> glossaryTextMap = new HashMap<>();

        String prompt = PromptUtils.GlossaryJsonPrompt(target, glossaryMapping, glossaryTextMap);
        assertNotNull(prompt);
        assertTrue(prompt.contains("Chinese (Simplified)"));
    }

    @Test
    void testJsonPrompt() {
        String target = "en";
        Map<Integer, String> originalTextMap = new HashMap<>();
        originalTextMap.put(1, "Hello");
        originalTextMap.put(2, "World");

        String prompt = PromptUtils.JsonPrompt(target, originalTextMap);
        assertNotNull(prompt);
        assertTrue(prompt.contains("English"));
    }

    @Test
    void testJsonPromptWithEmptyMap() {
        String target = "fr";
        Map<Integer, String> originalTextMap = new HashMap<>();

        String prompt = PromptUtils.JsonPrompt(target, originalTextMap);
        assertNotNull(prompt);
        assertTrue(prompt.contains("French"));
    }

    @Test
    void testGlossarySinglePrompt() {
        String targetLanguage = "en";
        String text = "Hello World";
        String glossaryMapping = "term:translation";

        String prompt = PromptUtils.GlossarySinglePrompt(targetLanguage, text, glossaryMapping);
        assertNotNull(prompt);
        assertTrue(prompt.contains("English"));
        assertTrue(prompt.contains("Hello World"));
        assertTrue(prompt.contains(glossaryMapping));
    }

    @Test
    void testGlossarySinglePromptWithEmptyText() {
        String targetLanguage = "ja";
        String text = "";
        String glossaryMapping = "term:translation";

        String prompt = PromptUtils.GlossarySinglePrompt(targetLanguage, text, glossaryMapping);
        assertNotNull(prompt);
        assertTrue(prompt.contains("Japanese"));
    }

    @Test
    void testSinglePrompt() {
        String targetLanguage = "en";
        String text = "Hello World";

        String prompt = PromptUtils.SinglePrompt(targetLanguage, text);
        assertNotNull(prompt);
        assertTrue(prompt.contains("English"));
        assertTrue(prompt.contains("Hello World"));
    }

    @Test
    void testSinglePromptWithDifferentLanguage() {
        String targetLanguage = "zh-CN";
        String text = "测试文本";

        String prompt = PromptUtils.SinglePrompt(targetLanguage, text);
        assertNotNull(prompt);
        assertTrue(prompt.contains("Chinese (Simplified)"));
        assertTrue(prompt.contains("测试文本"));
    }

    @Test
    void testSinglePromptWithEmptyText() {
        String targetLanguage = "es";
        String text = "";

        String prompt = PromptUtils.SinglePrompt(targetLanguage, text);
        assertNotNull(prompt);
        assertTrue(prompt.contains("Spanish"));
    }
}

