package com.bogda.common.utils;

import com.bogda.common.reporter.ExceptionReporterHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonUtils {
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    public static JsonNode readTree(String str) {
        try {
            return OBJECT_MAPPER.readTree(str);
        } catch (JsonProcessingException e) {
            ExceptionReporterHolder.report("JsonUtils.readTree", e);
            return null;
        }
    }

    // 将对象转换为JSON字符串
    public static String objectToJson(Object obj) {
        try {
            return obj != null ? OBJECT_MAPPER.writeValueAsString(obj) : null;
        } catch (JsonProcessingException e) {
            ExceptionReporterHolder.report("JsonUtils.objectToJson", e);
            return null;
        }
    }

    // 将JSON字符串转换为对象
    public static <T> T jsonToObject(String json, Class<T> clazz) {
        try {
            return json != null && !json.isEmpty() ? OBJECT_MAPPER.readValue(json, clazz) : null;
        } catch (JsonProcessingException e) {
            ExceptionReporterHolder.report("JsonUtils.jsonToObject", e);
            return null;
        }
    }

    public static <T> T jsonToObjectWithNull(String json, Class<T> clazz) {
        try {
            return json != null && !json.isEmpty() ? OBJECT_MAPPER.readValue(json, clazz) : null;
        } catch (JsonProcessingException e) {
            ExceptionReporterHolder.report("JsonUtils.jsonToObjectWithNull", e);
            return null;
        }
    }

    public static <T> T jsonToObjectWithNull(String json, TypeReference<T> typeRef) {
        try {
            return json != null && !json.isEmpty() ? OBJECT_MAPPER.readValue(json, typeRef) : null;
        } catch (Exception e) {
            ExceptionReporterHolder.report("JsonUtils.jsonToObjectWithNull", e);
            return null;
        }
    }

    public static <T> T jsonToObject(String json, TypeReference<T> typeRef) {
        try {
            return json != null && !json.isEmpty() ? OBJECT_MAPPER.readValue(json, typeRef) : null;
        } catch (JsonProcessingException e) {
            ExceptionReporterHolder.report("JsonUtils.jsonToObject", e);
            return null;
        }
    }

    // 判断一个string类型是不是Json数据
    public static boolean isJson(String str) {
        if (str == null || str.trim().isEmpty()) {
            return false;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(str);
            return node.isObject();
        } catch (Exception e) {

            return false;
        }
    }

    /**
     * 将String数据转化为json数据
     * */
    public static JsonNode stringToJson(String str) {
        try {
            return OBJECT_MAPPER.readTree(str);
        }catch (Exception e) {
            ExceptionReporterHolder.report("JsonUtils.stringToJson", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 修复 JSON 字符串内部的未转义双引号。
     * <p>
     * 规则（启发式）：
     * - 扫描 JSON 文本，进入字符串（遇到未转义的 "）后，遇到字符串内的 " 时：
     *   - 若其后（跳过空白）是 ',', '}', ']', ':' 或 EOF，视为字符串结束引号，保留
     *   - 否则视为内容引号，替换成 \"
     *
     * @param json 可能包含未转义双引号的 JSON 文本
     * @return 修复后的文本（若无需修复则原样返回）
     */
    public static String repairUnescapedQuotesInStringValues(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }

        StringBuilder out = new StringBuilder(json.length() + 16);
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (!inString) {
                if (c == '"') {
                    inString = true;
                    escaped = false;
                }
                out.append(c);
                continue;
            }

            // inString == true
            if (escaped) {
                out.append(c);
                escaped = false;
                continue;
            }

            if (c == '\\') {
                out.append(c);
                escaped = true;
                continue;
            }

            if (c == '"') {
                int j = i + 1;
                while (j < json.length() && Character.isWhitespace(json.charAt(j))) {
                    j++;
                }
                char next = (j < json.length()) ? json.charAt(j) : '\0';

                boolean looksLikeStringEnd = (next == ',' || next == '}' || next == ']' || next == ':' || next == '\0');
                if (looksLikeStringEnd) {
                    out.append('"');
                    inString = false;
                } else {
                    out.append("\\\"");
                }
                continue;
            }

            out.append(c);
        }

        return out.toString();
    }
}
