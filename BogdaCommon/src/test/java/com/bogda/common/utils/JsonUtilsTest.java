package com.bogda.common.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

public class JsonUtilsTest {

    @Test
    void testReadTreeWithValidJson() {
        String json = "{\"name\":\"test\",\"value\":123}";
        assertNotNull(JsonUtils.readTree(json));
    }

    @Test
    void testReadTreeWithInvalidJson() {
        String invalidJson = "{invalid json}";
        assertNull(JsonUtils.readTree(invalidJson));
    }

    @Test
    void testReadTreeWithNull() {
        assertThrows(IllegalArgumentException.class, () -> {
            JsonUtils.readTree(null);
        });
    }

    @Test
    void testObjectToJson() {
        Map<String, String> map = new HashMap<>();
        map.put("key", "value");
        String json = JsonUtils.objectToJson(map);
        assertNotNull(json);
        assertTrue(json.contains("key"));
        assertTrue(json.contains("value"));
    }

    @Test
    void testObjectToJsonWithNull() {
        assertNull(JsonUtils.objectToJson(null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testJsonToObject() {
        String json = "{\"name\":\"test\",\"age\":25}";
        Map<String, Object> result = JsonUtils.jsonToObject(json, Map.class);
        assertNotNull(result);
    }

    @Test
    void testJsonToObjectWithNull() {
        assertNull(JsonUtils.jsonToObject(null, Map.class));
    }

    @Test
    void testJsonToObjectWithEmptyString() {
        assertNull(JsonUtils.jsonToObject("", Map.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testJsonToObjectWithNullMethod() {
        String json = "{\"name\":\"test\"}";
        Map<String, Object> result = JsonUtils.jsonToObjectWithNull(json, Map.class);
        assertNotNull(result);
    }

    @Test
    void testJsonToObjectWithTypeReference() {
        String json = "{\"key\":\"value\"}";
        TypeReference<Map<String, String>> typeRef = new TypeReference<Map<String, String>>() {};
        Map<String, String> result = JsonUtils.jsonToObject(json, typeRef);
        assertNotNull(result);
    }

    @Test
    void testJsonToObjectWithNullTypeReference() {
        String json = "{\"key\":\"value\"}";
        TypeReference<Map<String, String>> typeRef = new TypeReference<Map<String, String>>() {};
        Map<String, String> result = JsonUtils.jsonToObjectWithNull(json, typeRef);
        assertNotNull(result);
    }

    @Test
    void testIsJsonWithValidJson() {
        assertTrue(JsonUtils.isJson("{\"key\":\"value\"}"));
    }

    @Test
    void testIsJsonWithInvalidJson() {
        assertFalse(JsonUtils.isJson("not a json"));
    }

    @Test
    void testIsJsonWithNull() {
        assertFalse(JsonUtils.isJson(null));
    }

    @Test
    void testIsJsonWithEmptyString() {
        assertFalse(JsonUtils.isJson(""));
    }

    @Test
    void testIsJsonWithWhitespace() {
        assertFalse(JsonUtils.isJson("   "));
    }

    @Test
    void testStringToJsonWithValidJson() {
        String json = "{\"key\":\"value\"}";
        assertNotNull(JsonUtils.stringToJson(json));
    }

    @Test
    void testStringToJsonWithInvalidJson() {
        String invalidJson = "{invalid}";
        assertThrows(RuntimeException.class, () -> {
            JsonUtils.stringToJson(invalidJson);
        });
    }
}

