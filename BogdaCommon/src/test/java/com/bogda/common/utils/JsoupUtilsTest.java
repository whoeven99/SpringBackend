package com.bogda.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsoupUtilsTest {

    @Test
    void testIsHtml() {
        // Test case 1: Valid HTML content
        String htmlContent = "<html><body><p>Test</p></body></html>";
        assertTrue(JsoupUtils.isHtml(htmlContent));

        // Test case 2: Plain text without HTML tags
        String plainText = "This is just plain text.";
        assertFalse(JsoupUtils.isHtml(plainText));

        // Test case 3: HTML-like content but not valid HTML
        String invalidHtml = "<html>This is not valid</html";
        assertTrue(JsoupUtils.isHtml(invalidHtml));

        // Test case 4: Empty string
        String emptyContent = "";
        assertFalse(JsoupUtils.isHtml(emptyContent));

        // Test case 5: Null content
//        String nullContent = null;
//        assertFalse(JsoupUtils.isHtml(nullContent));
    }
}
