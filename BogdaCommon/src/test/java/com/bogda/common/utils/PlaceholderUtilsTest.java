package com.bogda.common.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

public class PlaceholderUtilsTest {

    @Test
    void testGetHandlePrompt() {
        String prompt = PlaceholderUtils.getHandlePrompt("English");
        assertNotNull(prompt);
        assertTrue(prompt.contains("English"));
        assertTrue(prompt.contains("Translate"));
    }

    @Test
    void testGetHandlePromptWithDifferentLanguage() {
        String prompt = PlaceholderUtils.getHandlePrompt("Chinese");
        assertNotNull(prompt);
        assertTrue(prompt.contains("Chinese"));
    }

    @Test
    void testBuildDescriptionPrompt() {
        String prompt = PlaceholderUtils.buildDescriptionPrompt(
                "Product Name",
                "Category",
                "Description",
                "Keywords",
                "Image URL",
                "Image Description",
                "Professional",
                "template",
                "Brand",
                "Structure",
                "English",
                "description",
                "Brand Word",
                "Brand Slogan"
        );
        assertNotNull(prompt);
        assertTrue(prompt.contains("Product Name"));
        assertTrue(prompt.contains("Category"));
        assertTrue(prompt.contains("Description"));
        assertTrue(prompt.contains("Keywords"));
        assertTrue(prompt.contains("Professional"));
        assertTrue(prompt.contains("English"));
    }

    @Test
    void testBuildDescriptionPromptWithNullValues() {
        String prompt = PlaceholderUtils.buildDescriptionPrompt(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "English",
                "description",
                null,
                null
        );
        assertNotNull(prompt);
        assertTrue(prompt.contains("English"));
    }

    @Test
    void testBuildDescriptionPromptWithEmptyStrings() {
        String prompt = PlaceholderUtils.buildDescriptionPrompt(
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "English",
                "description",
                "",
                ""
        );
        assertNotNull(prompt);
        assertTrue(prompt.contains("English"));
    }

    @Test
    void testBuildNonNullMap() {
        Object[][] pairs = new Object[][]{
                {"key1", "value1"},
                {"key2", "value2"},
                {"key3", null}
        };
        Map<String, String> map = PlaceholderUtils.buildNonNullMap(pairs);
        assertNotNull(map);
        assertEquals(2, map.size());
        assertEquals("value1", map.get("key1"));
        assertEquals("value2", map.get("key2"));
        assertFalse(map.containsKey("key3"));
    }

    @Test
    void testBuildNonNullMapWithAllNull() {
        Object[][] pairs = new Object[][]{
                {"key1", null},
                {"key2", null}
        };
        Map<String, String> map = PlaceholderUtils.buildNonNullMap(pairs);
        assertNotNull(map);
        assertTrue(map.isEmpty());
    }

    @Test
    void testBuildNonNullMapWithEmptyArray() {
        Object[][] pairs = new Object[0][];
        Map<String, String> map = PlaceholderUtils.buildNonNullMap(pairs);
        assertNotNull(map);
        assertTrue(map.isEmpty());
    }
}

