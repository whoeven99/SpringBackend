package com.bogdatech.utils;

import org.junit.jupiter.api.Assertions;
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

    @Test
    void testDecodeHtmlEntitiesOnce() {
        String input = "&lt;p&gt;Hello &amp; World&lt;/p&gt;";
        List<String> result = HtmlUtils.parseHtml(input, "en");

        Assertions.assertEquals(List.of("Hello & World"), result);
    }

    @Test
    void testDecodeHtmlEntitiesMultipleTimes() {
        String input = "&amp;lt;p&amp;gt;Hello&amp;lt;/p&amp;gt;";
        List<String> result = HtmlUtils.parseHtml(input, "en");

        // 应该解码至少 2 次，最终得到 <p>Hello</p>
        Assertions.assertEquals(List.of("Hello"), result);
    }

    @Test
    void testPlainTextNoHtml() {
        String input = "Just a plain text";
        List<String> result = HtmlUtils.parseHtml(input, "en");

        Assertions.assertEquals(List.of("Just a plain text"), result);
    }

    @Test
    void testSimpleHtmlTagExtraction() {
        String input = "<p>Hello World</p>";
        List<String> result = HtmlUtils.parseHtml(input, "en");

        Assertions.assertEquals(List.of("Hello World"), result);
    }

    @Test
    void testHtmlTagAddLangToHtml() {
        String input = "<html lang=\"en\"><body>Hello</body></html>";
        List<String> result = HtmlUtils.parseHtml(input, "zh");
        Assertions.assertEquals(List.of("Hello"), result);
    }

    @Test
    void testHtmlFragment_NoHtmlRoot() {
        String input = "<div>Hello <b>World</b></div>";
        List<String> result = HtmlUtils.parseHtml(input, "jp");

        Assertions.assertEquals(List.of("Hello", "World"), result);
    }

    @Test
    void testDuplicateTextRemoved() {
        String input = "<p>Hello</p><span>Hello</span>";
        List<String> result = HtmlUtils.parseHtml(input, "en");

        Assertions.assertEquals(List.of("Hello"), result);
    }

    @Test
    void testNestedHtmlExtraction() {
        String input = "<div><p>Hello <span>World</span></p></div>";
        List<String> result = HtmlUtils.parseHtml(input, "en");

        Assertions.assertEquals(List.of("Hello", "World"), result);
    }

    @Test
    void testIgnoreEmptyOrWhitespaceText() {
        String input = "<div>\n  <p> Hello </p>\n  <p>   </p>\n</div>";
        List<String> result = HtmlUtils.parseHtml(input, "en");

        Assertions.assertEquals(List.of("Hello"), result);
    }

    @Test
    void testHtmlWithAttributesContainingText_NotExtracted() {
        String input = "<div title=\"Hello\">Test</div>";
        List<String> result = HtmlUtils.parseHtml(input, "en");

        // 只提取节点文本，不提取属性文本
        Assertions.assertEquals(List.of("Test"), result);
        Assertions.assertFalse(result.contains("Hello"));
    }

//    @Test
//    void testComplexHtmlWithEntitiesAndTags() {
//        String input = "<div>&lt;span&gt;Hello&lt;/span&gt; &amp; World</div><div title=\"Hello\">Test</div><p>你好 <span>世界</span></p>";
//        List<String> result = HtmlUtils.parseHtml(input, "en");
//
//        // 解码后应看到: <span>Hello</span> & World
//        // 提取的是 "Hello" 与 "& World"
//        Assertions.assertEquals(List.of("Hello", "& World"), result);
//    }

    @Test
    void testHtmlWithChineseCharacters() {
        String input = "<p>你好 <span>世界</span></p>";
        List<String> result = HtmlUtils.parseHtml(input, "zh");

        Assertions.assertEquals(List.of("你好", "世界"), result);
    }

    @Test
    void testHtmlWithEmoji() {
        String input = "<p>Hello 😊 World</p>";
        List<String> result = HtmlUtils.parseHtml(input, "en");

        Assertions.assertEquals(List.of("Hello 😊 World"), result);
    }

}
