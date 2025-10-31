package com.bogdatech.utils;

import com.bogdatech.entity.DTO.SimpleMultipartFileDTO;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class StringUtilsTest {

    @Test
    void testReplaceSpaces() {
        String input = "Hello World";
        String result = StringUtils.replaceSpaces(input, "_");
        assertEquals("Hello_World", result);
    }

    @Test
    void testJudgeStringType() {
        // TODO
//        assertEquals("CURLY_BRACKET_ARRAY", StringUtils.judgeStringType("{{xx[x].xx}}"));
//        assertEquals("DOUBLE_BRACES", StringUtils.judgeStringType("{{example}}"));
//        assertEquals("PERCENTAGE_CURLY_BRACES", StringUtils.judgeStringType("%{example}"));
//        assertEquals("DOUBLE_CURLY_BRACKET_AND_HUNDRED", StringUtils.judgeStringType("{%example%}"));
//        assertEquals("PLAIN_TEXT", StringUtils.judgeStringType("plain text"));
    }

    @Test
    void testReplaceLanguage() {
        String prompt = "{{Chinese}} is a language.";
        String result = StringUtils.replaceLanguage(prompt, "English", "resource", "industry");
        assertEquals("English is a language.", result);
    }

    @Test
    void testCountWords() {
        String text = "Hello, world! This is a test.";
        int count = StringUtils.countWords(text);
        assertEquals(6, count);
    }

    @Test
    void testReplaceDot() {
        String text = "example.com";
        String result = StringUtils.replaceDot(text);
        assertEquals("example-com", result);
    }

    @Test
    void testIsNumber() {
        assertTrue(StringUtils.isNumber("123"));
        assertTrue(StringUtils.isNumber("-123.45"));
        assertFalse(StringUtils.isNumber("abc"));
    }

    @Test
    void testParseShopName() {
        String shopName = "example.myshopify.com";
        String result = StringUtils.parseShopName(shopName);
        assertEquals("example", result);
    }

    @Test
    void testNormalizeHtml() {
        String html = "<div>\n\nHello\n\n</div>";
        String result = StringUtils.normalizeHtml(html);
        assertEquals("<div> Hello </div>", result);
    }

    @Test
    void testReplaceHyphensWithSpaces() {
        String input = "hello-world";
        String result = StringUtils.replaceHyphensWithSpaces(input);
        assertEquals("hello world", result);
    }

    @Test
    void testIsValueBlank() {
        assertTrue(StringUtils.isValueBlank(" "));
        // TODO
//        assertFalse(StringUtils.isValueBlank("not blank"));
    }

    @Test
    void testIsValidBase64() {
        String validBase64 = Base64.getEncoder().encodeToString("test".getBytes());
        assertTrue(StringUtils.isValidBase64(validBase64));
        assertFalse(StringUtils.isValidBase64("invalid base64"));
    }

    @Test
    void testGenerate8DigitNumber() {
        String number = StringUtils.generate8DigitNumber();
        assertEquals(8, number.length());
        assertTrue(number.matches("\\d+"));
    }
}
