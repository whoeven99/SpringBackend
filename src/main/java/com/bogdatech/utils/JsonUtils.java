package com.bogdatech.utils;


import com.bogdatech.exception.ClientException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import static com.bogdatech.enums.ErrorEnum.JSON_PARSE_ERROR;
import static com.bogdatech.logic.TranslateService.OBJECT_MAPPER;
import static com.bogdatech.utils.CaseSensitiveUtils.appInsights;

public class JsonUtils {

    public static JsonNode readTree(String str) {
        try {
            return OBJECT_MAPPER.readTree(str);
        } catch (JsonProcessingException e) {
            appInsights.trackException(e);
            return null;
        }
    }

    // 将对象转换为JSON字符串
    public static String objectToJson(Object obj) {
        try {
            return obj != null ? OBJECT_MAPPER.writeValueAsString(obj) : null;
        } catch (JsonProcessingException e) {
            appInsights.trackException(e);
            return null;
        }
    }

    // 将JSON字符串转换为对象
    public static <T> T jsonToObject(String json, Class<T> clazz) {
        try {
            return json != null && !json.isEmpty() ? OBJECT_MAPPER.readValue(json, clazz) : null;
        } catch (JsonProcessingException e) {
            appInsights.trackException(e);
            throw new ClientException(JSON_PARSE_ERROR.getErrMsg() + "   " + e.getMessage());
        }
    }

    public static <T> T jsonToObjectWithNull(String json, Class<T> clazz) {
        try {
            return json != null && !json.isEmpty() ? OBJECT_MAPPER.readValue(json, clazz) : null;
        } catch (JsonProcessingException e) {
            appInsights.trackException(e);
            return null;
        }
    }

    public static <T> T jsonToObjectWithNull(String json, TypeReference<T> typeRef) {
        try {
            return json != null && !json.isEmpty() ? OBJECT_MAPPER.readValue(json, typeRef) : null;
        } catch (Exception e) {
            appInsights.trackException(e);
            return null;
        }
    }

    public static <T> T jsonToObject(String json, TypeReference<T> typeRef) {
        try {
            return json != null && !json.isEmpty() ? OBJECT_MAPPER.readValue(json, typeRef) : null;
        } catch (JsonProcessingException e) {
            appInsights.trackException(e);
            throw new ClientException(JSON_PARSE_ERROR.getErrMsg() + "   " + e.getMessage());
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

    //解析JSON数据，获取message消息
    public static String getMessage(String json) {
        String message = null;
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);

            JsonNode messageNode = root
                    .path("translationsRegister")
                    .path("userErrors")
                    .path(0)
                    .path("message");

            if (!messageNode.isMissingNode()) {
                appInsights.trackTrace("updateShopifyDataByTranslateTextRequest Message: " + messageNode.asText());
                message = messageNode.asText();
            } else {
                message = json;
                appInsights.trackTrace("updateShopifyDataByTranslateTextRequest   Message not found");
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return message;
    }

    /**
     * 将String数据转化为json数据
     * */
    public static JsonNode stringToJson(String str) {
        try {
            return OBJECT_MAPPER.readTree(str);
        }catch (Exception e) {
            appInsights.trackTrace("clickTranslation String to Json errors: " + e);
            throw new RuntimeException(e);
        }
    }
}
