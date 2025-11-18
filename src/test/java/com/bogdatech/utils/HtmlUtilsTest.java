package com.bogdatech.utils;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HtmlUtilsTest {

    @Test
    void testReplaceBackBasic() {
        String html = "<html><body><div>Hello</div><p>World</p></body></html>";
        List<String> originalTexts = Arrays.asList("Hello", "World");
        Map<Integer, String> translatedValueMap = new HashMap<>();
        translatedValueMap.put(0, "Hola");
        translatedValueMap.put(1, "Mundo");

        String result = HtmlUtils.replaceBack(html, originalTexts, translatedValueMap);

        assertEquals("<html><body><div>Hola</div><p>Mundo</p></body></html>", result);
    }

    @Test
    void testReplaceBackWithSpecialCharacters() {
        String html = "<html><body><div>Hello &amp; Welcome</div><p>World</p></body></html>";

        List<String> originalTexts = Arrays.asList("Hello & Welcome", "World");
        Map<Integer, String> translatedValueMap = new HashMap<>();
        translatedValueMap.put(0, "Hola & Bienvenido");
        translatedValueMap.put(1, "Mundo");

        String result = HtmlUtils.replaceBack(html, originalTexts, translatedValueMap);

        assertEquals("<html><body><div>Hola & Bienvenido</div><p>Mundo</p></body></html>", result);
    }

    @Test
    void testReplaceBackWithPartialTranslations() {
        String html = "<html><body><div>Hello</div><p>World</p></body></html>";
        List<String> originalTexts = Arrays.asList("Hello", "World");
        Map<Integer, String> translatedValueMap = new HashMap<>();
        translatedValueMap.put(0, "Hola");

        String result = HtmlUtils.replaceBack(html, originalTexts, translatedValueMap);

        assertEquals("<html><body><div>Hola</div><p>World</p></body></html>", result);
    }

    @Test
    void testReplaceBackWithNoTranslations() {
        String html = "<html><body><div>Hello</div><p>World</p></body></html>";
        List<String> originalTexts = Arrays.asList("Hello", "World");
        Map<Integer, String> translatedValueMap = new HashMap<>();

        String result = HtmlUtils.replaceBack(html, originalTexts, translatedValueMap);

        assertEquals("<html><body><div>Hello</div><p>World</p></body></html>", result);
    }

    @Test
    void testReplaceBackWithEmptyInputs() {
        String html = "<html><body><div></div><p></p></body></html>";
        List<String> originalTexts = Arrays.asList();
        Map<Integer, String> translatedValueMap = new HashMap<>();

        String result = HtmlUtils.replaceBack(html, originalTexts, translatedValueMap);

        assertEquals("<html><body><div></div><p></p></body></html>", result);
    }
}
