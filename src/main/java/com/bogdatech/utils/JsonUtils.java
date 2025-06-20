package com.bogdatech.utils;


import com.bogdatech.exception.ClientException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.bogdatech.enums.ErrorEnum.JSON_PARSE_ERROR;

public class JsonUtils {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 将对象转换为JSON字符串
    public static String objectToJson(Object obj) {
        try {
            return obj != null ? objectMapper.writeValueAsString(obj) : null;
        } catch (JsonProcessingException e) {
            throw new ClientException(JSON_PARSE_ERROR.getErrMsg() + "   " + e.getMessage());
        }
    }

    // 将JSON字符串转换为对象
    public static <T> T jsonToObject(String json, Class<T> clazz) {
        try {
            return json != null && !json.isEmpty() ? objectMapper.readValue(json, clazz) : null;
        } catch (JsonProcessingException e) {
            throw new ClientException(JSON_PARSE_ERROR.getErrMsg() + "   " + e.getMessage());
        }
    }

    //判断一个string类型是不是Json数据
    public static boolean isJson(String str) {
        try {
            //清除空格
            str = str.replaceAll(" ", "");
            ObjectMapper mapper = new ObjectMapper();
            mapper.readTree(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    //解析JSON数据，获取message消息
    public static String getMessage(String json) {
        String message = null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(json);

            JsonNode messageNode = root
                    .path("translationsRegister")
                    .path("userErrors")
                    .path(0)
                    .path("message");

            if (!messageNode.isMissingNode()) {
                System.out.println("Message: " + messageNode.asText());
                message = messageNode.asText();
            } else {
                message = json;
                System.out.println("Message not found");
            }
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return message;
    }
}
