package com.bogdatech.utils;

import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LiquidHtmlTranslatorUtilsTest {

    @Test
    void testIsHtmlEntity() {
        String input = "&lt;div&gt;Hello &amp; Welcome&lt;/div&gt;";
        String expected = "<div>Hello & Welcome</div>";
        String result = LiquidHtmlTranslatorUtils.isHtmlEntity(input);
        assertEquals(expected, result);

        input = "No entities here";
        expected = "No entities here";
        result = LiquidHtmlTranslatorUtils.isHtmlEntity(input);
        assertEquals(expected, result);
    }

    @Test
    void testParseHtml() {
        String htmlWithTag = "<html><body><p>Test</p></body></html>";
        Document docWithTag = LiquidHtmlTranslatorUtils.parseHtml(htmlWithTag, "en", true);
        assertNotNull(docWithTag);
        assertEquals("en", docWithTag.selectFirst("html").attr("lang"));

        String htmlWithoutTag = "<p>Test</p>";
        Document docWithoutTag = LiquidHtmlTranslatorUtils.parseHtml(htmlWithoutTag, "fr", false);
        assertNotNull(docWithoutTag);
        assertEquals("<p>Test</p>", docWithoutTag.body().html());
    }
}
