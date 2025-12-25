package com.bogda.common.utils;

import com.bogda.common.enums.ErrorEnum;
import com.bogda.common.exception.ClientException;
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
            CaseSensitiveUtils.appInsights.trackException(e);
            return null;
        }
    }

    // 将对象转换为JSON字符串
    public static String objectToJson(Object obj) {
        try {
            return obj != null ? OBJECT_MAPPER.writeValueAsString(obj) : null;
        } catch (JsonProcessingException e) {
            CaseSensitiveUtils.appInsights.trackException(e);
            return null;
        }
    }

    // 将JSON字符串转换为对象
    public static <T> T jsonToObject(String json, Class<T> clazz) {
        try {
            return json != null && !json.isEmpty() ? OBJECT_MAPPER.readValue(json, clazz) : null;
        } catch (JsonProcessingException e) {
            CaseSensitiveUtils.appInsights.trackException(e);
            throw new ClientException(ErrorEnum.JSON_PARSE_ERROR.getErrMsg() + "   " + e.getMessage());
        }
    }

    public static <T> T jsonToObjectWithNull(String json, Class<T> clazz) {
        try {
            return json != null && !json.isEmpty() ? OBJECT_MAPPER.readValue(json, clazz) : null;
        } catch (JsonProcessingException e) {
            CaseSensitiveUtils.appInsights.trackException(e);
            return null;
        }
    }

    public static <T> T jsonToObjectWithNull(String json, TypeReference<T> typeRef) {
        try {
            return json != null && !json.isEmpty() ? OBJECT_MAPPER.readValue(json, typeRef) : null;
        } catch (Exception e) {
            CaseSensitiveUtils.appInsights.trackException(e);
            return null;
        }
    }

    public static <T> T jsonToObject(String json, TypeReference<T> typeRef) {
        try {
            return json != null && !json.isEmpty() ? OBJECT_MAPPER.readValue(json, typeRef) : null;
        } catch (JsonProcessingException e) {
            CaseSensitiveUtils.appInsights.trackException(e);
            throw new ClientException(ErrorEnum.JSON_PARSE_ERROR.getErrMsg() + "   " + e.getMessage());
        }
    }

    // 判断一个string类型是不是Json数据
    public static boolean isJson(String str) {
        try {
            //清除空格
            str = str.replaceAll(" ", "");
            OBJECT_MAPPER.readTree(str);
            return true;
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
            CaseSensitiveUtils.appInsights.trackTrace("clickTranslation String to Json errors: " + e);
            throw new RuntimeException(e);
        }
    }
}
