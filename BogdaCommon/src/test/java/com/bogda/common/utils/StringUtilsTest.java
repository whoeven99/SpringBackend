package com.bogda.common.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StringUtilsTest {
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
    void testParseShopName() {
        String shopName = "example.myshopify.com";
        String result = StringUtils.parseShopName(shopName);
        assertEquals("example", result);
    }

    @Test
    void testReplaceHyphensWithSpaces() {
        String input = "hello-world";
        String result = StringUtils.replaceHyphensWithSpaces(input);
        assertEquals("hello world", result);
    }

    @Test
    void testIsValueBlank() {
//        assertTrue(StringUtils.isValueBlank(" "));
//        assertFalse(StringUtils.isValueBlank("not blank"));
    }

    @Test
    void testGenerate8DigitNumber() {
        String number = StringUtils.generate8DigitNumber();
        assertEquals(8, number.length());
        assertTrue(number.matches("\\d+"));
    }
}
