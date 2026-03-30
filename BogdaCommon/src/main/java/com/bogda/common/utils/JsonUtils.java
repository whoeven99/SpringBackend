package com.bogda.common.utils;

import com.bogda.common.reporter.ExceptionReporterHolder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** 判断一个string类型是不是Json数据 */
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
     * 深度修复 LLM 返回的畸形 JSON
     */
    public static String highlyRobustRepair(String json) {
        if (json == null || json.trim().isEmpty()) return json;

        String fixed = json.trim();

        // 1. 处理被截断的 JSON (补齐结尾)
        if (fixed.startsWith("{") && !fixed.endsWith("}")) {
            if (!fixed.endsWith("\"")) fixed += "\"";
            fixed += "}";
        }

        // 2. 修复缺失的键值对逗号: "val" "2": -> "val", "2":
        fixed = fixed.replaceAll("(\"\\s*)(?=\"\\d+\"\\s*:)", "$1, ");

        // 3. 修复内容中未转义的双引号
        fixed = repairUnescapedQuotesAdvanced(fixed);

        return fixed;
    }

    private static String repairUnescapedQuotesAdvanced(String json) {
        StringBuilder sb = new StringBuilder();
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            // 简单判定是否为未被转义的引号
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                if (!inString) {
                    inString = true;
                    sb.append(c);
                } else {
                    // 探测下一个非空字符，判断当前引号是否为结束引号
                    int j = i + 1;
                    while (j < json.length() && Character.isWhitespace(json.charAt(j))) j++;

                    if (j < json.length()) {
                        char next = json.charAt(j);
                        // 合法的结束引号后通常跟着 , } 或 :
                        if (next == ',' || next == '}' || next == ':') {
                            inString = false;
                            sb.append(c);
                        } else {
                            // 否则视为内容中的引号，强行转义
                            sb.append("\\\"");
                        }
                    } else {
                        sb.append(c);
                        inString = false;
                    }
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 判断一个文本是不是List格式的数据 */
    public static boolean isListFormat(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return false;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            List<String> list = mapper.readValue(
                    jsonStr,
                    new TypeReference<List<String>>() {}
            );
            return list != null; // 只要能解析成功就算匹配
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

    /**
     * 修复多重转义的 Unicode (\\\\u -> \\u)
     */
    public static String decodeDoubleEscapedUnicode(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Pattern p = Pattern.compile("\\\\{2,4}u([0-9a-fA-F]{4})");
        Matcher m = p.matcher(text);
        return m.replaceAll("\\\\u$1");
    }

    // 修复JSON字符串中缺少的引号
    public static String fixMissingQuote(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            return jsonStr;
        }
        String fixed = jsonStr.replaceAll("(\"\\d+\"\\s*:\\s*)([^\"\\s])", "$1\"$2");
        fixed = fixed.replaceAll("(:\\s*\"[^\"]*)(?=\\s*,|\\s*})", "$1\"");
        return fixed;
    }
}
