package com.bogdatech.utils;


import com.bogdatech.exception.ClientException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.bogdatech.enums.ErrorEnum.JSON_PARSE_ERROR;

public class JsonUtil {
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
}
