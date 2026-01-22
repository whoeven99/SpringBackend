package com.bogda.api;

import com.bogda.common.entity.DO.GlossaryDO;
import com.bogda.service.logic.GlossaryService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class GlossaryServiceTest {
    @Test
    void should_match_case_insensitive_glossary() {
        String content = "The Origin of the Steel Tongue Drum";

        Map<String, GlossaryDO> glossaryMap = new HashMap<>();
        glossaryMap.put("tongue drum", new GlossaryDO("tongue drum", "舌鼓", 0));

        Map<String, GlossaryDO> usedGlossaryMap = new HashMap<>();

        boolean result = GlossaryService.hasGlossary(content, glossaryMap, usedGlossaryMap);

        assertTrue(result);
        assertEquals(1, usedGlossaryMap.size());
        assertTrue(usedGlossaryMap.containsKey("tongue drum"));
    }

    @Test
    void should_not_match_case_sensitive_glossary() {
        String content = "Steel Tongue Drum";

        Map<String, GlossaryDO> glossaryMap = new HashMap<>();
        glossaryMap.put("tongue drum", new GlossaryDO("tongue drum", "舌鼓", 1)); // 区分大小写

        Map<String, GlossaryDO> usedGlossaryMap = new HashMap<>();

        boolean result = GlossaryService.hasGlossary(content, glossaryMap, usedGlossaryMap);

        assertFalse(result);
        assertTrue(usedGlossaryMap.isEmpty());
    }

    @Test
    void should_match_case_sensitive_exact_match() {
        String content = "This is a tongue drum";

        Map<String, GlossaryDO> glossaryMap = new HashMap<>();
        glossaryMap.put("tongue drum", new GlossaryDO("tongue drum", "舌鼓", 0));

        Map<String, GlossaryDO> usedGlossaryMap = new HashMap<>();

        boolean result = GlossaryService.hasGlossary(content, glossaryMap, usedGlossaryMap);

        assertTrue(result);
        assertEquals(1, usedGlossaryMap.size());
    }

    @Test
    void should_match_multiple_glossaries() {
        String content = "Steel Tongue Drum is a percussion instrument";

        Map<String, GlossaryDO> glossaryMap = new HashMap<>();
        glossaryMap.put("tongue drum", new GlossaryDO("tongue drum", "舌鼓", 0));
        glossaryMap.put("percussion", new GlossaryDO("percussion", "打击乐", 1));

        Map<String, GlossaryDO> usedGlossaryMap = new HashMap<>();

        boolean result = GlossaryService.hasGlossary(content, glossaryMap, usedGlossaryMap);

        assertTrue(result);
        assertEquals(2, usedGlossaryMap.size());
    }

    @Test
    void should_return_false_when_content_is_null() {
        Map<String, GlossaryDO> glossaryMap = new HashMap<>();
        glossaryMap.put("test", new GlossaryDO("Test", "测试", 0));

        boolean result = GlossaryService.hasGlossary(null, glossaryMap, new HashMap<>());

        assertFalse(result);
    }

    @Test
    void should_return_false_when_usedGlossaryMap_is_null() {
        String content = "test content";

        Map<String, GlossaryDO> glossaryMap = new HashMap<>();
        glossaryMap.put("test", new GlossaryDO("Test", "测试", 1));

        boolean result = GlossaryService.hasGlossary(content, glossaryMap, null);

        assertFalse(result);
    }
}
