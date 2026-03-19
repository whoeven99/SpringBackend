package com.bogda.common.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

public class PromptUtilsTest {

    @Test
    void testBuildDynamicJsonPrompt_ShouldEncodeDoubleQuotesInValuesBeforeJson() {
        Map<Integer, String> sourceMap = new HashMap<>();
        sourceMap.put(1, "a \"b\" c");

        String prompt = PromptUtils.buildDynamicJsonPrompt(
                "en",
                sourceMap,
                null,
                null,
                null
        );

        // JSON 文本中应出现 \\u0022（两个反斜杠），而不是原始的 \"
        String expected = "\\\\" + "u0022";
        assertTrue(prompt.contains(expected));
        assertFalse(prompt.contains("\\\"b\\\""));
    }

}

